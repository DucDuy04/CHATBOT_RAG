package KLTN.RAG_CHATBOT_BE.ingest.normalize;

import KLTN.RAG_CHATBOT_BE.ingest.chunking.ChunkingService2;
import KLTN.RAG_CHATBOT_BE.ingest.parser.RawTableBlock;
import KLTN.RAG_CHATBOT_BE.ingest.parser.RawTableModel;
import KLTN.RAG_CHATBOT_BE.record.DocumentChunk;
import KLTN.RAG_CHATBOT_BE.record.Section;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression: structured table ingest must not reintroduce deprecated chunk paths.
 */
class NormalizedTableSuppressionTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Set<String> DEPRECATED_CHUNK_TYPES = Set.of("table_row_group", "text_table_like");

    private NormalizedTableService normalizedTableService;
    private ChunkingService2 chunkingService;

    @BeforeEach
    void setUp() {
        normalizedTableService = new NormalizedTableService();
        chunkingService = new ChunkingService2();
    }

    @Test
    void docxStructuredTable_emitsNormalizedRowsOnly() throws Exception {
        RawTableModel table = TestRawTableFixtures.docxSimpleAbcTable();
        NormalizedTableService.NormalizationResult result = normalize(table);
        assertTrue(result.success(), result.failureReason());

        NormalizedTableRow row = findRowContaining(result, "v1");
        assertNotNull(row);
        Map<String, String> cells = parseCells(row.cellsJson());
        assertEquals("v1", cells.get("A"));
        assertEquals("v2", cells.get("B"));
        assertEquals("v3", cells.get("C"));

        List<DocumentChunk> chunks = chunkRawTable(table, "tbl_simple");
        assertChunkTypes(chunks, Set.of("normalized_table_row", "table_summary"));
        assertNoDeprecatedChunkTypes(chunks);
        assertEquals(0, chunkingService.getLastIngestMetrics().getValuesDroppedCount());
    }

    @Test
    void broadParentHeader_doesNotPolluteColumnKeys() throws Exception {
        RawTableModel table = TestRawTableFixtures.docxBroadParentHeaderTable();
        NormalizedTableService.NormalizationResult result = normalize(table);
        assertTrue(result.success(), result.failureReason());

        NormalizedTableRow row = findRowContaining(result, "v1");
        assertNotNull(row);
        Map<String, String> cells = parseCells(row.cellsJson());
        assertEquals("v1", cells.get("A"));
        assertEquals("v2", cells.get("B"));
        assertEquals("v3", cells.get("C"));
        assertFalse(cells.containsKey("Parent"));
        assertFalse(cells.containsKey("Parent_2"));
        assertFalse(cells.containsKey("Parent_3"));
    }

    @Test
    void groupHeaderRow_doesNotCorruptFollowingDataRowSlots() throws Exception {
        RawTableModel table = TestRawTableFixtures.docxScheduleWithGroupHeaderRow();
        NormalizedTableService.NormalizationResult result = normalize(table);
        assertTrue(result.success(), result.failureReason());

        NormalizedTableRow row = findRowContaining(result, "LUA1012");
        assertNotNull(row);
        Map<String, String> cells = parseCells(row.cellsJson());
        assertEquals("1", cells.get("STT"));
        assertEquals("LUA1012", cells.get("Mã học phần"));
        assertEquals("1 - 2", cells.get("Tiết học"));
        assertEquals("E301", cells.get("Phòng"));
        assertNotEquals("1 - 2", cells.get("Mã học phần"));
        assertNotEquals("E301", cells.get("Tên lớp học phần"));
    }

    @Test
    void noValuesDropped_allDataRowValuesSurvive() throws Exception {
        RawTableModel table = TestRawTableFixtures.docxScheduleWithGroupHeaderRow();
        NormalizedTableService.NormalizationResult result = normalize(table);
        assertTrue(result.success());

        assertEquals(0, result.stats().valuesDroppedCount());
        assertTrue(result.stats().valuesPreservedCount() > 0);

        List<String> expectedValues = List.of(
                "1", "LUA1012", "Pháp luật Việt Nam đại cương - Nhóm 1", "Nhóm 1",
                "2", "0", "Nguyễn Thị Vân Anh", "08/09/2025", "1 - 2", "E301"
        );
        String allCanonical = result.rows().stream()
                .map(NormalizedTableRow::canonicalText)
                .filter(t -> t != null)
                .collect(Collectors.joining(" "));
        for (String value : expectedValues) {
            assertTrue(allCanonical.contains(value), "missing value: " + value);
        }
    }

    @Test
    void structuredIngest_metricsShowNoDeprecatedPaths() {
        RawTableModel docx = TestRawTableFixtures.docxSimpleAbcTable();
        RawTableModel pdf = TestRawTableFixtures.pdfCoordinateTable();
        chunkRawTable(docx, "tbl_docx");
        chunkRawTable(pdf, "tbl_pdf");

        TableIngestMetrics metrics = chunkingService.getLastIngestMetrics();
        assertTrue(metrics.getStructuredTablesNormalized() > 0);
        assertEquals(0, metrics.getPdfTablesUsingMarkdownBridge());
        assertEquals(0, metrics.getDocxTablesUsingMarkdownBridge());
        assertEquals(0, metrics.getValuesDroppedCount());
        assertTrue(metrics.getNormalizedTableRowChunks() > 0);
        assertTrue(metrics.getTableSummaries() > 0);
    }

    private NormalizedTableService.NormalizationResult normalize(RawTableModel table) {
        return normalizedTableService.normalizeRawTable(table, new NormalizedTableService.NormalizationRequest(
                null, "Section", null, 1, 1, 0, null));
    }

    private List<DocumentChunk> chunkRawTable(RawTableModel table, String markerId) {
        Section section = new Section(
                "Test",
                1,
                1,
                "[RAW_TABLE_REF:" + markerId + "]\n",
                0,
                1,
                List.of(new RawTableBlock(markerId, table)));
        return chunkingService.processSections2(List.of(section));
    }

    private static void assertChunkTypes(List<DocumentChunk> chunks, Set<String> allowedTableTypes) {
        Set<String> tableChunkTypes = chunks.stream()
                .map(DocumentChunk::chunkType)
                .filter(t -> t != null && (t.contains("table") || t.equals("normalized_table_row")))
                .collect(Collectors.toSet());
        assertFalse(tableChunkTypes.isEmpty());
        assertTrue(tableChunkTypes.stream().allMatch(allowedTableTypes::contains),
                "unexpected table chunk types: " + tableChunkTypes);
    }

    private static void assertNoDeprecatedChunkTypes(List<DocumentChunk> chunks) {
        List<String> deprecated = chunks.stream()
                .map(DocumentChunk::chunkType)
                .filter(DEPRECATED_CHUNK_TYPES::contains)
                .toList();
        assertTrue(deprecated.isEmpty(), "deprecated chunk types emitted: " + deprecated);
    }

    private static NormalizedTableRow findRowContaining(
            NormalizedTableService.NormalizationResult result,
            String needle) {
        return result.rows().stream()
                .filter(r -> r.canonicalText() != null && r.canonicalText().contains(needle))
                .findFirst()
                .orElse(null);
    }

    private static Map<String, String> parseCells(String cellsJson) throws Exception {
        return JSON.readValue(cellsJson, new TypeReference<>() {});
    }
}
