package KLTN.RAG_CHATBOT_BE.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Task 23J3 — generic raw table suppression + retrieval priority tests.
 */
class NormalizedTableSuppressionTest {

    private NormalizedTableService normalizer;
    private ChunkingService2 chunkingService2;
    private KeywordSearchService keywordSearch;

    @BeforeEach
    void setUp() {
        normalizer = new NormalizedTableService();
        chunkingService2 = new ChunkingService2(normalizer);
        keywordSearch = new KeywordSearchService(null);
    }

    @Test
    void cellOverlapSuppression_removesTableLine() {
        String pageText = """
                Intro paragraph.
                ABC123 Item A 01/01/2026 Room R101
                Footer note.
                """;
        String md = """
                | Code | Name | Date | Room |
                | --- | --- | --- | --- |
                | ABC123 | Item A | 01/01/2026 | R101 |
                """;
        var profile = normalizer.buildSuppressionProfile(md);
        var result = normalizer.suppressRawTableText(pageText, profile);

        assertThat(result.text()).contains("Intro paragraph").contains("Footer note");
        assertThat(result.text()).doesNotContain("ABC123");
        assertThat(result.suppressedRawChars()).isGreaterThan(0);
    }

    @Test
    void keepNonTableText_whileSuppressingTableRow() {
        KLTN.RAG_CHATBOT_BE.record.Section section = new KLTN.RAG_CHATBOT_BE.record.Section(
                "Handbook",
                1, 1,
                """
                Students must read the handbook carefully.

                ABC123 Item A 01/01/2026 Room R101

                [TABLE_START]
                | Code | Name | Date | Room |
                | --- | --- | --- | --- |
                | ABC123 | Item A | 01/01/2026 | R101 |
                [TABLE_END]
                """,
                0, 1);

        List<KLTN.RAG_CHATBOT_BE.record.DocumentChunk> chunks = chunkingService2.processSections2(List.of(section));
        String prose = chunks.stream()
                .filter(c -> "text".equals(c.chunkType()))
                .map(KLTN.RAG_CHATBOT_BE.record.DocumentChunk::content)
                .reduce("", (a, b) -> a + "\n" + b);

        assertThat(prose).contains("Students must read the handbook carefully");
        assertThat(prose).doesNotContain("ABC123");
        assertThat(chunks.stream().anyMatch(c -> "normalized_table_row".equals(c.chunkType()))).isTrue();
    }

    @Test
    void nonMarkdownTableLine_suppressedWhenOverlapsCells() {
        String raw = """
                STT Name Teacher Room
                1 ABC123 Math John R101
                2 DEF456 Physics Jane R102
                """;
        String md = """
                | Code | Name | Teacher | Room |
                | --- | --- | --- | --- |
                | ABC123 | Math | John | R101 |
                | DEF456 | Physics | Jane | R102 |
                """;
        var profile = normalizer.buildSuppressionProfile(md);
        var result = normalizer.suppressRawTableText(raw, profile);

        assertThat(result.text()).doesNotContain("ABC123").doesNotContain("DEF456");
        assertThat(result.suppressedLines()).isGreaterThan(0);
    }

    @Test
    void repeatedHeader_suppressedFromText() {
        String pageText = """
                Code Name Room
                ABC123 Item A R101
                """;
        String md = """
                | Code | Name | Room |
                | --- | --- | --- |
                | ABC123 | Item A | R101 |
                """;
        var profile = normalizer.buildSuppressionProfile(md);
        var result = normalizer.suppressRawTableText(pageText, profile);

        assertThat(result.text()).doesNotContain("Code Name Room");
    }

    @Test
    void postChunkLeakageFilter_dropsHighDensityText() {
        var profile = normalizer.buildSuppressionProfile("""
                | Code | Name | Room |
                | --- | --- | --- |
                | ABC123 | Item A | R101 |
                | DEF456 | Item B | R102 |
                """);
        String leaky = """
                1 ABC123 Item A John R101
                2 DEF456 Item B Jane R102
                3 GHI789 Item C Bob R103
                4 JKL012 Item D Ann R104
                """;
        assertThat(normalizer.shouldDropLeakyTextChunk(leaky, profile)).isTrue();
    }

    @Test
    void retrievalBoost_normalizedRowBeatsTextMegaChunk() throws Exception {
        var boostField = KeywordSearchService.class.getDeclaredField("tableRowBoost");
        boostField.setAccessible(true);
        boostField.set(keywordSearch, 1.2);

        KLTN.RAG_CHATBOT_BE.domain.document.DocumentChunk row = KLTN.RAG_CHATBOT_BE.domain.document.DocumentChunk.builder()
                .chunkType("normalized_table_row")
                .content("Group 2 room H101 teacher John")
                .sectionTitle("Schedule")
                .build();
        KLTN.RAG_CHATBOT_BE.domain.document.DocumentChunk plain = KLTN.RAG_CHATBOT_BE.domain.document.DocumentChunk.builder()
                .chunkType("text")
                .content("Group 2 room H101 teacher John\n".repeat(80))
                .sectionTitle("Schedule")
                .build();

        String question = "Group 2 room which teacher";
        var signals = QuerySignalExtractor.extract(question);
        double rowScore = keywordSearch.scoreChunk(row, signals, Map.of(), question);
        double textScore = keywordSearch.scoreChunk(plain, signals, Map.of(), question);
        assertThat(rowScore).isGreaterThan(textScore);
    }

    @Test
    void noHardcode_usesGenericCodeNameRoom() {
        String md = """
                | Code | Name | Room |
                | --- | --- | --- |
                | XYZ999 | Widget | R777 |
                """;
        var result = normalizer.normalize(new NormalizedTableService.NormalizationRequest(
                md, "Generic", null, 1, 1, 0, null));
        assertThat(result.success()).isTrue();
        assertThat(result.rows().get(0).cells()).containsEntry("Code", "XYZ999");
    }

    @Test
    void normalizeFail_noFallbackText() {
        String bad = "| only one col |\n| --- |\n| x |";
        KLTN.RAG_CHATBOT_BE.record.Section section = new KLTN.RAG_CHATBOT_BE.record.Section("S", 1, 1,
                "Plain intro.\n[TABLE_START]\n" + bad + "\n[TABLE_END]", 0, 1);
        List<KLTN.RAG_CHATBOT_BE.record.DocumentChunk> chunks = chunkingService2.processSections2(List.of(section));

        assertThat(chunks.stream().map(KLTN.RAG_CHATBOT_BE.record.DocumentChunk::chunkType))
                .noneMatch(t -> t.contains("fallback") || "text_table_like".equals(t));
        assertThat(chunkingService2.getLastIngestMetrics().getFailedTables()).isEqualTo(1);
        assertThat(chunks.stream().filter(c -> "text".equals(c.chunkType()))
                .anyMatch(c -> c.content().contains("| x |"))).isFalse();
    }

    @Test
    void ragRetrieval_demotesLeakyTextWhenNormalizedPresent() {
        KLTN.RAG_CHATBOT_BE.domain.document.DocumentChunk row = KLTN.RAG_CHATBOT_BE.domain.document.DocumentChunk.builder()
                .id(java.util.UUID.randomUUID())
                .chunkType("normalized_table_row")
                .content("Group 2 room H101")
                .build();
        KLTN.RAG_CHATBOT_BE.domain.document.DocumentChunk text = KLTN.RAG_CHATBOT_BE.domain.document.DocumentChunk.builder()
                .id(java.util.UUID.randomUUID())
                .chunkType("text")
                .content("""
                        1 ABC123 Item A John R101
                        2 DEF456 Item B Jane R102
                        3 GHI789 Item C Bob R103
                        4 JKL012 Item D Ann R104
                        """)
                .build();

        List<RagRetrievalService.ScoredChunk> scored = List.of(
                new RagRetrievalService.ScoredChunk(text, 0.9),
                new RagRetrievalService.ScoredChunk(row, 0.7));
        var adjusted = RagRetrievalService.demoteLeakyTextCandidates("Group 2 room table row", scored);

        assertThat(adjusted.getFirst().chunk().getChunkType()).isEqualTo("normalized_table_row");
    }
}
