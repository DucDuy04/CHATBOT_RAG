package KLTN.RAG_CHATBOT_BE.ingest.normalize;

import KLTN.RAG_CHATBOT_BE.ingest.parser.RawTableModel;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class NormalizedTableIngestTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private NormalizedTableService service;

    @BeforeEach
    void setUp() {
        service = new NormalizedTableService();
    }

    @Test
    void docxScheduleRow_mapsByPhysicalColIndex_notPackedIntoStt() throws Exception {
        RawTableModel table = TestRawTableFixtures.docxScheduleWithGroupHeaderRow();
        NormalizedTableService.NormalizationResult result = normalize(table);

        assertTrue(result.success(), result.failureReason());
        NormalizedTableRow luaRow = findRowContaining(result, "LUA1012");
        assertNotNull(luaRow);

        Map<String, String> cells = parseCells(luaRow.cellsJson());
        assertEquals("1", cells.get("STT"));
        assertEquals("LUA1012", cells.get("Mã học phần"));
        assertEquals("Pháp luật Việt Nam đại cương - Nhóm 1", cells.get("Tên lớp học phần"));
        assertEquals("1 - 2", cells.get("Tiết học"));
        assertEquals("E301", cells.get("Phòng"));
        assertFalse(cells.getOrDefault("STT", "").contains("LUA1012"));
    }

    @Test
    void docxScheduleRow_groupHeaderRow_doesNotCorruptFollowingDataRow() throws Exception {
        RawTableModel table = TestRawTableFixtures.docxScheduleWithGroupHeaderRow();
        NormalizedTableService.NormalizationResult result = normalize(table);
        assertTrue(result.success());

        boolean hasGroupLabel = result.rows().stream()
                .anyMatch(r -> r.canonicalText() != null
                        && r.canonicalText().contains("Ngành: Công nghệ thông tin"));
        assertTrue(hasGroupLabel || result.rows().size() >= 1);

        NormalizedTableRow dataRow = findRowContaining(result, "LUA1012");
        assertNotNull(dataRow);
        Map<String, String> cells = parseCells(dataRow.cellsJson());
        assertNotEquals("1 - 2", cells.get("Mã học phần"));
        assertNotEquals("E301", cells.get("Tên lớp học phần"));
    }

    @Test
    void docxCurriculumRow_mapsKhóaNgànhAndHocKyOneToOne() throws Exception {
        RawTableModel table = TestRawTableFixtures.docxCurriculumRow();
        NormalizedTableService.NormalizationResult result = normalize(table);

        assertTrue(result.success(), result.failureReason());
        NormalizedTableRow row = findRowContaining(result, "TIN1093");
        assertNotNull(row);

        Map<String, String> cells = parseCells(row.cellsJson());
        assertEquals("TIN1093", cells.get("Mã HP"));
        assertEquals("Công nghệ thông tin", cells.get("Khóa ngành"));
        assertEquals("Học kỳ 1", cells.get("Học kỳ"));
    }

    @Test
    void pdfCoordinateTable_usesOverlapPath_notDocxDirectIndex() throws Exception {
        RawTableModel table = TestRawTableFixtures.pdfCoordinateTable();
        NormalizedTableService.NormalizationResult result = normalize(table);

        assertTrue(result.success(), result.failureReason());
        NormalizedTableRow row = findRowContaining(result, "ABC101");
        assertNotNull(row);

        Map<String, String> cells = parseCells(row.cellsJson());
        assertEquals("1", cells.get("STT"));
        assertEquals("ABC101", cells.get("Mã HP"));
        assertEquals("Môn mẫu", cells.get("Tên HP"));
    }

    @Test
    void docxSyntheticRow_oneValuePerColumn_noPackingIntoStt() throws Exception {
        RawTableModel table = TestRawTableFixtures.docxCurriculumRow();
        NormalizedTableService.NormalizationResult result = normalize(table);
        assertTrue(result.success());

        NormalizedTableRow row = findRowContaining(result, "TIN1093");
        Map<String, String> cells = parseCells(row.cellsJson());
        assertFalse(cells.getOrDefault("Mã HP", "").contains("Tin học"));
        assertFalse(cells.getOrDefault("STT", "").contains("TIN1093"));
    }

    private NormalizedTableService.NormalizationResult normalize(RawTableModel table) {
        return service.normalizeRawTable(table, new NormalizedTableService.NormalizationRequest(
                null, "Section", null, 1, 1, 0, null));
    }

    private static NormalizedTableRow findRowContaining(
            NormalizedTableService.NormalizationResult result,
            String needle
    ) {
        return result.rows().stream()
                .filter(r -> r.canonicalText() != null && r.canonicalText().contains(needle))
                .findFirst()
                .orElse(null);
    }

    private static Map<String, String> parseCells(String cellsJson) throws Exception {
        return JSON.readValue(cellsJson, new TypeReference<>() {});
    }
}
