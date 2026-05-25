package KLTN.RAG_CHATBOT_BE.service;

import KLTN.RAG_CHATBOT_BE.record.DocumentChunk;
import KLTN.RAG_CHATBOT_BE.record.Section;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Task 23J — generic normalized table ingest unit tests (no domain hardcoding).
 */
class NormalizedTableIngestTest {

    private NormalizedTableService normalizer;
    private ChunkingService2 chunkingService2;

    @BeforeEach
    void setUp() {
        normalizer = new NormalizedTableService();
        chunkingService2 = new ChunkingService2(normalizer);
    }

    @Test
    void simpleTableNormalization_mapsCells() {
        String md = """
                | Code | Name | Date |
                | --- | --- | --- |
                | ABC123 | Item A | 01/01/2026 |
                """;

        var result = normalizer.normalize(req(md, "Section", 1));
        assertThat(result.success()).isTrue();
        assertThat(result.rows()).hasSize(1);
        assertThat(result.rows().get(0).cells())
                .containsEntry("Code", "ABC123")
                .containsEntry("Name", "Item A")
                .containsEntry("Date", "01/01/2026");
    }

    @Test
    void oneHeaderPerColumn_mapsSimpleSyntheticCellsExactly() {
        String md = """
                | A | B | C |
                | --- | --- | --- |
                | v1 | v2 | v3 |
                """;

        var result = normalizer.normalize(req(md, "S", 1));

        assertThat(result.success()).isTrue();
        assertThat(result.rows().getFirst().cells())
                .containsExactly(
                        Map.entry("A", "v1"),
                        Map.entry("B", "v2"),
                        Map.entry("C", "v3"));
        assertThat(result.rows().getFirst().cells().keySet())
                .doesNotContain("A B", "B C", "A B C");
    }

    @Test
    void independentHeaders_mapToSameColumnValues() {
        String md = """
                | H1 | H2 | H3 |
                | --- | --- | --- |
                | x | y | z |
                """;

        var result = normalizer.normalize(req(md, "S", 1));

        assertThat(result.success()).isTrue();
        assertThat(result.rows().getFirst().cells())
                .containsExactly(
                        Map.entry("H1", "x"),
                        Map.entry("H2", "y"),
                        Map.entry("H3", "z"));
    }

    @Test
    void compactHeaderContainingAnotherColumnHeader_fallsBackForThatColumn() {
        String md = """
                | Alpha Beta | Beta |
                | --- | --- |
                | v1 | v2 |
                """;

        var result = normalizer.normalize(req(md, "S", 1));

        assertThat(result.success()).isTrue();
        assertThat(result.rows().getFirst().cells())
                .containsExactly(
                        Map.entry("col_1", "v1"),
                        Map.entry("Beta", "v2"));
        assertThat(result.stats().compactHeaderSuspiciousCount()).isEqualTo(1);
        assertThat(result.stats().compactHeaderFallbackCount()).isEqualTo(1);
        assertThat(result.stats().multiColumnHeaderRejectedCount()).isEqualTo(1);
    }

    @Test
    void repeatedSpanningHeaderAcrossAdjacentColumns_fallsBackWithoutDroppingValues() {
        String md = """
                | Alpha Beta | Alpha Beta |
                | --- | --- |
                | v1 | v2 |
                """;

        var result = normalizer.normalize(req(md, "S", 1));

        assertThat(result.success()).isTrue();
        assertThat(result.rows().getFirst().cells())
                .containsExactly(
                        Map.entry("col_1", "v1"),
                        Map.entry("col_2", "v2"));
        assertThat(result.rows().getFirst().cells().values())
                .containsExactly("v1", "v2");
        assertThat(result.stats().valuesPreservedCount()).isEqualTo(2);
        assertThat(result.stats().valuesDroppedCount()).isZero();
    }

    @Test
    void separatePhysicalHeaderFragments_mapSeparately() {
        String md = """
                | Alpha | Beta |
                | --- | --- |
                | v1 | v2 |
                """;

        var result = normalizer.normalize(req(md, "S", 1));

        assertThat(result.success()).isTrue();
        assertThat(result.rows().getFirst().cells())
                .containsExactly(
                        Map.entry("Alpha", "v1"),
                        Map.entry("Beta", "v2"));
    }

    @Test
    void mergedHeaderFromMultiplePhysicalPositions_isRejectedWhenOtherColumnProvesOverlap() {
        String md = """
                | Parent | Parent |
                | Alpha Beta | Beta |
                | --- | --- |
                | v1 | v2 |
                """;

        var result = normalizer.normalize(req(md, "S", 1));

        assertThat(result.success()).isTrue();
        assertThat(result.rows().getFirst().cells())
                .containsExactly(
                        Map.entry("col_1", "v1"),
                        Map.entry("Beta", "v2"));
        assertThat(result.rows().getFirst().cells().keySet()).doesNotContain("Alpha Beta");
    }

    @Test
    void repeatedHeader_isSkipped_notDataRow() {
        String page1 = """
                | Code | Name |
                | --- | --- |
                | A1 | Math |
                """;
        var r1 = normalizer.normalize(req(page1, "S", 1));
        assertThat(r1.success()).isTrue();
        assertThat(r1.rows()).hasSize(1);

        String page2 = """
                | Code | Name |
                | --- | --- |
                | A2 | Physics |
                """;
        var r2 = normalizer.normalize(
                new NormalizedTableService.NormalizationRequest(
                        page2, "S", null, 2, 2, 1, r1.updatedState()));
        assertThat(r2.success()).isTrue();
        assertThat(r2.rows()).hasSize(1);
        assertThat(r2.rows().get(0).cells()).containsEntry("Code", "A2");
        assertThat(r2.updatedState().rowIndexCounter()).isEqualTo(2);
    }

    @Test
    void continuationPageWithoutHeader_mapsWithInheritedHeaders() {
        String page1 = """
                | Code | Name | Room |
                | --- | --- | --- |
                | A1 | Math | R101 |
                """;
        var r1 = normalizer.normalize(req(page1, "S", 1));

        String page2 = """
                | A2 | Physics | R102 |
                """;
        var r2 = normalizer.normalize(
                new NormalizedTableService.NormalizationRequest(
                        page2, "S", null, 2, 2, 1, r1.updatedState()));

        assertThat(r2.success()).isTrue();
        assertThat(r2.rows().get(0).cells())
                .containsEntry("Code", "A2")
                .containsEntry("Name", "Physics")
                .containsEntry("Room", "R102");
    }

    @Test
    void groupRow_propagatesGroupContext() {
        String md = """
                | Category: Science |  |
                | Code | Name |
                | --- | --- |
                | A1 | Math |
                | A2 | Physics |
                """;

        var result = normalizer.normalize(req(md, "S", 1));
        assertThat(result.success()).isTrue();
        assertThat(result.rows()).hasSize(2);
        assertThat(result.rows().get(0).groupContext()).contains("Science");
        assertThat(result.rows().get(1).groupContext()).contains("Science");
    }

    @Test
    void wrappedRow_mergeDescription() {
        List<List<String>> rows = List.of(
                List.of("A1", "Long description part 1"),
                List.of("", "continued part 2")
        );
        List<List<String>> merged = NormalizedTableService.mergeWrappedRows(rows, 2);
        assertThat(merged).hasSize(1);
        assertThat(merged.get(0).get(1)).isEqualTo("Long description part 1 continued part 2");
    }

    @Test
    void multiLineHeaderRows_chooseChildHeadersGenerically() {
        String md = """
                |  | Primary | Secondary |
                | Index | Label | Value |
                | --- | --- | --- |
                | 1 | Alpha | 10 |
                """;

        var result = normalizer.normalize(req(md, "S", 1));

        assertThat(result.success()).isTrue();
        assertThat(result.rows().getFirst().cells())
                .containsEntry("Index", "1")
                .containsEntry("Label", "Alpha")
                .containsEntry("Value", "10");
        assertThat(result.stats().multiRowHeadersMerged()).isEqualTo(1);
        assertThat(result.stats().headerSiblingContaminationPrevented()).isEqualTo(2);
    }

    @Test
    void parentChildHeaders_chooseLeafChild() {
        String md = """
                | ParentA | ParentA | ParentB |
                | Child1 | Child2 | Child3 |
                | --- | --- | --- |
                | V1 | V2 | V3 |
                """;

        var result = normalizer.normalize(req(md, "S", 1));

        assertThat(result.success()).isTrue();
        assertThat(result.rows().getFirst().cells())
                .containsExactly(
                        Map.entry("Child1", "V1"),
                        Map.entry("Child2", "V2"),
                        Map.entry("Child3", "V3"));
    }

    @Test
    void parentHeaderUsedOnlyWhenChildMissing() {
        String md = """
                | ParentA | ParentB |
                |  | ChildB |
                | --- | --- |
                | V1 | V2 |
                """;

        var result = normalizer.normalize(req(md, "S", 1));

        assertThat(result.success()).isTrue();
        assertThat(result.rows().getFirst().cells())
                .containsExactly(
                        Map.entry("ParentA", "V1"),
                        Map.entry("ChildB", "V2"));
    }

    @Test
    void siblingHeaderContamination_isForbidden() {
        String md = """
                | A | B | C |
                | --- | --- | --- |
                | V1 | V2 | V3 |
                """;

        var cells = normalizer.normalize(req(md, "S", 1)).rows().getFirst().cells();

        assertThat(headerForValue(cells, "V1")).doesNotContain("B").doesNotContain("C");
        assertThat(headerForValue(cells, "V2")).doesNotContain("A").doesNotContain("C");
        assertThat(headerForValue(cells, "V3")).doesNotContain("A").doesNotContain("B");
    }

    @Test
    void pseudoTableConversion_handlesUnevenSpacing() {
        String raw = """
                Key      Label         Amount
                A1       Alpha item    10
                B22      Beta item     200
                """;

        String converted = chunkingService2.detectAndConvertTextTables(raw);
        assertThat(converted).contains("[TABLE_START]");

        Section section = new Section("S", 1, 1, converted, 0, 1);
        List<DocumentChunk> chunks = chunkingService2.processSections2(List.of(section));
        List<DocumentChunk> rows = chunks.stream()
                .filter(c -> "normalized_table_row".equals(c.chunkType()))
                .toList();

        assertThat(rows).hasSize(2);
        assertThat(rows.get(1).cellsJson()).contains("B22").contains("Beta item").contains("200");
    }

    @Test
    void sparseContinuationRow_fillsTrailingEmptyCells() {
        String md = """
                | Key | Description | Window |
                | --- | --- | --- |
                | A1 | Submit request |  |
                |  |  | Jan 01 - Jan 05 |
                """;

        var result = normalizer.normalize(req(md, "S", 1));

        assertThat(result.success()).isTrue();
        assertThat(result.rows()).hasSize(1);
        assertThat(result.rows().getFirst().cells())
                .containsEntry("Description", "Submit request")
                .containsEntry("Window", "Jan 01 - Jan 05");
        assertThat(result.stats().continuationRowsMerged()).isEqualTo(1);
        assertThat(result.stats().sparseRowsRepaired()).isEqualTo(1);
    }

    @Test
    void crossPageContinuation_carriesHeadersAndMetrics() {
        String page1 = """
                | Key | Label | Amount |
                | --- | --- | --- |
                | A1 | Alpha | 10 |
                """;
        var r1 = normalizer.normalize(req(page1, "S", 1));

        String page2 = """
                | B2 | Beta | 20 |
                | C3 | Gamma | 30 |
                """;
        var r2 = normalizer.normalize(new NormalizedTableService.NormalizationRequest(
                page2, "S", null, 2, 2, 1, r1.updatedState()));

        assertThat(r2.success()).isTrue();
        assertThat(r2.rows()).hasSize(2);
        assertThat(r2.rows().getFirst().cells()).containsEntry("Label", "Beta");
        assertThat(r2.stats().crossPageHeaderCarryCount()).isEqualTo(1);
    }

    @Test
    void uncertainHeaders_useGenericColumnKeysWithoutDroppingValues() {
        String md = """
                | 1 | Alpha | 10 |
                | 2 | Beta | 20 |
                """;

        var result = normalizer.normalize(req(md, "S", 1));

        assertThat(result.success()).isTrue();
        assertThat(result.rows().getFirst().cells())
                .containsEntry("col_1", "1")
                .containsEntry("col_2", "Alpha")
                .containsEntry("col_3", "10");
        assertThat(result.stats().rowsWithGenericColumnKeys()).isGreaterThanOrEqualTo(3);
    }

    @Test
    void duplicateSameColumnHeaderFragment_isDeduped() {
        String md = """
                | A A | B B |
                | --- | --- |
                | v1 | v2 |
                """;

        var result = normalizer.normalize(req(md, "S", 1));

        assertThat(result.success()).isTrue();
        assertThat(result.rows().getFirst().cells())
                .containsExactly(
                        Map.entry("A", "v1"),
                        Map.entry("B", "v2"));
    }

    @Test
    void rawCellsJsonItselfIsCleanWithoutDisplayCleanup() {
        String md = """
                | A | B | C |
                | --- | --- | --- |
                | v1 | v2 | v3 |
                """;

        var row = normalizer.normalize(req(md, "S", 1)).rows().getFirst();

        assertThat(row.cellsJson()).isEqualTo("{\"A\":\"v1\",\"B\":\"v2\",\"C\":\"v3\"}");
    }

    @Test
    void valuesArePreservedExactlyOnceInRawCellsJson() {
        String md = """
                | A | B | C |
                | --- | --- | --- |
                | v 1 | v-2 | v/3 |
                """;

        var values = normalizer.normalize(req(md, "S", 1)).rows().getFirst().cells().values();

        assertThat(values).containsExactly("v 1", "v-2", "v/3");
    }

    @Test
    void rawTableTextSuppression_textChunkExcludesTableLines() {
        Section section = new Section(
                "Intro",
                1, 1,
                """
                Paragraph outside table.

                | Code | Name |
                | --- | --- |
                | X1 | Alpha |

                More prose after.
                """,
                0, 1);

        List<DocumentChunk> chunks = chunkingService2.processSections2(List.of(section));
        List<DocumentChunk> textChunks = chunks.stream()
                .filter(c -> "text".equals(c.chunkType()))
                .toList();

        assertThat(textChunks).isNotEmpty();
        String allText = String.join("\n", textChunks.stream().map(DocumentChunk::content).toList());
        assertThat(allText).contains("Paragraph outside");
        assertThat(allText).contains("More prose");
        assertThat(allText).doesNotContain("| X1 |");
        assertThat(allText).doesNotContain("Alpha");
    }

    @Test
    void normalizeFail_noFallbackChunks() {
        String bad = "| only one col |\n| --- |\n| x |";
        var result = normalizer.normalize(req(bad, "S", 1));
        assertThat(result.success()).isFalse();

        Section section = new Section("S", 1, 1, "[TABLE_START]\n" + bad + "\n[TABLE_END]", 0, 1);
        List<DocumentChunk> chunks = chunkingService2.processSections2(List.of(section));
        assertThat(chunks.stream().map(DocumentChunk::chunkType))
                .noneMatch(t -> t.contains("fallback") || "text_table_like".equals(t));
        assertThat(chunks.stream().filter(c -> "text".equals(c.chunkType()))
                .noneMatch(c -> c.content().contains("| x |"))).isTrue();
    }

    @Test
    void noRawTableFallbackChunkTypesAreCreated() {
        String md = """
                | A | B | C |
                | --- | --- | --- |
                | v1 | v2 | v3 |
                """;
        Section section = new Section("S", 1, 1, "[TABLE_START]\n" + md + "\n[TABLE_END]", 0, 1);

        List<DocumentChunk> chunks = chunkingService2.processSections2(List.of(section));

        assertThat(chunks.stream().map(DocumentChunk::chunkType))
                .doesNotContain("table_row_group", "text_table_like");
        assertThat(chunks.stream().filter(c -> "text".equals(c.chunkType()))
                .noneMatch(c -> c.content().contains("| v1 | v2 | v3 |"))).isTrue();
    }

    @Test
    void tableSummary_hasColumnsRowCountAndPageRange() {
        String md = """
                | Code | Name |
                | --- | --- |
                | A1 | Math |
                | A2 | Physics |
                """;
        var result = normalizer.normalize(req(md, "My Section", 3));
        assertThat(result.tableSummaryContent())
                .contains("Các cột")
                .contains("Code")
                .contains("Số dòng đã chuẩn hóa: 2")
                .contains("Trang: 3");
    }

    @Test
    void retrievalBoost_normalizedRowPreferredOverPlainText() throws Exception {
        KeywordSearchService keywordSearch = new KeywordSearchService(null);
        var boostField = KeywordSearchService.class.getDeclaredField("tableRowBoost");
        boostField.setAccessible(true);
        boostField.set(keywordSearch, 1.2);
        KLTN.RAG_CHATBOT_BE.domain.document.DocumentChunk row = KLTN.RAG_CHATBOT_BE.domain.document.DocumentChunk.builder()
                .chunkType("normalized_table_row")
                .content("Nhom 2 phong H101 thu 3")
                .sectionTitle("Schedule")
                .build();
        KLTN.RAG_CHATBOT_BE.domain.document.DocumentChunk plain = KLTN.RAG_CHATBOT_BE.domain.document.DocumentChunk.builder()
                .chunkType("text")
                .content("Nhom 2 phong H101 thu 3")
                .sectionTitle("Schedule")
                .build();

        var signals = QuerySignalExtractor.extract("Nhom 2 phong thu may");
        String tableQuery = "Nhom 2 phong table row column";
        var tableSignals = QuerySignalExtractor.extract(tableQuery);
        double rowBoost = invokeChunkTypeBoost(keywordSearch, row, tableSignals, tableQuery);
        double textBoost = invokeChunkTypeBoost(keywordSearch, plain, tableSignals, tableQuery);
        assertThat(rowBoost).isGreaterThan(textBoost);
    }

    private static NormalizedTableService.NormalizationRequest req(String md, String section, int page) {
        return new NormalizedTableService.NormalizationRequest(
                md, section, null, page, page, 0, null);
    }

    private static String headerForValue(Map<String, String> cells, String value) {
        return cells.entrySet().stream()
                .filter(e -> value.equals(e.getValue()))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElseThrow();
    }

    private static double invokeChunkTypeBoost(
            KeywordSearchService svc,
            KLTN.RAG_CHATBOT_BE.domain.document.DocumentChunk chunk,
            QuerySignalExtractor.QuerySignals signals,
            String question
    ) {
        try {
            var m = KeywordSearchService.class.getDeclaredMethod(
                    "chunkTypeBoost",
                    KLTN.RAG_CHATBOT_BE.domain.document.DocumentChunk.class,
                    QuerySignalExtractor.QuerySignals.class,
                    String.class);
            m.setAccessible(true);
            return (double) m.invoke(svc, chunk, signals, question);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
