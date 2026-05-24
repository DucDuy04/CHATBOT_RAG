package KLTN.RAG_CHATBOT_BE.service;

import KLTN.RAG_CHATBOT_BE.domain.document.Document;
import KLTN.RAG_CHATBOT_BE.domain.document.DocumentChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Task 23I — generic hybrid keyword scoring (no domain/file hardcoding).
 */
class HybridKeywordSearchTest {

    private KeywordSearchService keywordSearchService;

    @BeforeEach
    void setUp() {
        keywordSearchService = new KeywordSearchService(null);
        setField(keywordSearchService, "hybridEnabled", true);
        setField(keywordSearchService, "exactMatchBoost", 2.0);
        setField(keywordSearchService, "phraseMatchBoost", 1.5);
        setField(keywordSearchService, "tableRowBoost", 1.2);
        setField(keywordSearchService, "tableSummaryBoost", 0.8);
    }

    @Test
    void genericIdentifier_chunkA_beats_chunkB() {
        String query = "Ma ABC123 co gia bao nhieu?";
        QuerySignalExtractor.QuerySignals signals = QuerySignalExtractor.extract(query);
        DocumentChunk chunkA = chunk("ABC123 san pham gia 100", "text");
        DocumentChunk chunkB = chunk("DEF999 san pham gia 200", "text");

        double scoreA = score(query, signals, chunkA);
        double scoreB = score(query, signals, chunkB);

        assertTrue(scoreA > scoreB, "ABC123 chunk should score higher than DEF999");
    }

    @Test
    void genericDateRange_chunkWithMatchingDates_ranksHigher() {
        String query = "Su kien bao tri dien ra tu ngay nao den ngay nao?";
        QuerySignalExtractor.QuerySignals signals = QuerySignalExtractor.extract(query);
        DocumentChunk chunkA = chunk("Lich bao tri: 01/05/2026 den 05/05/2026", "text");
        DocumentChunk chunkB = chunk("Lich bao tri du kien trong thang 8", "text");

        double scoreA = score(query, signals, chunkA);
        double scoreB = score(query, signals, chunkB);

        assertTrue(scoreA > scoreB);
    }

    @Test
    void genericGroupLabel_nhom2_beats_nhom18() {
        String query = "Lop Nhom 2 hoc o phong nao?";
        QuerySignalExtractor.QuerySignals signals = QuerySignalExtractor.extract(query);
        DocumentChunk chunkA = chunk("Nhom 2 phong H101 thu 3", "normalized_table_row");
        DocumentChunk chunkB = chunk("Nhom 18 phong H202 thu 5", "normalized_table_row");

        double scoreA = score(query, signals, chunkA);
        double scoreB = score(query, signals, chunkB);

        assertTrue(scoreA > scoreB, "Nhom 2 should beat Nhom 18 for Nhom 2 query");
    }

    @Test
    void genericHkPeriod_hk2_beats_hk1() {
        String query = "Danh sach hoc phan hoc ky 2 gom nhung gi?";
        QuerySignalExtractor.QuerySignals signals = QuerySignalExtractor.extract(query);
        DocumentChunk chunkA = chunk("HK2 danh sach mon hoc", "text");
        DocumentChunk chunkB = chunk("HK1 danh sach mon hoc", "text");

        double scoreA = score(query, signals, chunkA);
        double scoreB = score(query, signals, chunkB);

        assertTrue(scoreA > scoreB, "HK2 chunk should beat HK1 for HK2 query");
    }

    @Test
    void genericPolicyCode_pol2025_withDate_ranksTop() {
        String query = "Chinh sach POL2025 co hieu luc ngay nao?";
        QuerySignalExtractor.QuerySignals signals = QuerySignalExtractor.extract(query);
        DocumentChunk chunkA = chunk("POL2025 hieu luc tu 01/01/2025", "text");
        DocumentChunk chunkB = chunk("POL1999 het hieu luc 2020", "text");

        double scoreA = score(query, signals, chunkA);
        double scoreB = score(query, signals, chunkB);

        assertTrue(scoreA > scoreB);
        assertTrue(signals.identifiers().stream().anyMatch(id -> id.contains("POL2025")));
    }

    @Test
    void mergeCandidates_dedupesAndMarksBoth() {
        UUID sharedId = UUID.randomUUID();
        DocumentChunk vectorChunk = DocumentChunk.builder()
                .id(sharedId)
                .content("shared")
                .chunkType("text")
                .build();
        DocumentChunk keywordOnly = chunk("keyword only", "text");

        KeywordSearchService.KeywordSearchResult kwResult = new KeywordSearchService.KeywordSearchResult(
                List.of(
                        new KeywordSearchService.ScoredKeywordChunk(vectorChunk, 5.0, 1.0),
                        new KeywordSearchService.ScoredKeywordChunk(keywordOnly, 3.0, 0.8)),
                Map.of(sharedId, 1.0, keywordOnly.getId(), 0.8),
                QuerySignalExtractor.extract("test"));

        List<KeywordSearchService.MergedCandidate> merged = KeywordSearchService.mergeCandidates(
                List.of(vectorChunk),
                java.util.Set.of(sharedId),
                kwResult);

        assertEquals(2, merged.size());
        assertEquals(1, KeywordSearchService.countBothSource(merged));
        assertTrue(merged.stream().anyMatch(m -> m.source() == KeywordSearchService.CandidateSource.BOTH));
        assertTrue(merged.stream().anyMatch(m -> m.source() == KeywordSearchService.CandidateSource.KEYWORD));
    }

    @Test
    void querySignals_buildNgrams_fromNormalizedQuery() {
        String normalized = QuerySignalExtractor.normalize(
                "Thoi gian nghi Tet Nguyen Dan trong so tay la khi nao?");
        List<String> ngrams = QuerySignalExtractor.buildNgrams(normalized, 2, 5);
        assertFalse(ngrams.isEmpty());
        assertTrue(ngrams.stream().anyMatch(g -> g.contains("tet") && g.contains("nguyen")));
    }

    private double score(String query, QuerySignalExtractor.QuerySignals signals, DocumentChunk chunk) {
        List<DocumentChunk> corpus = List.of(chunk);
        Map<String, Double> idf = Map.of();
        return keywordSearchService.scoreChunk(chunk, signals, idf, query);
    }

    private static DocumentChunk chunk(String content, String type) {
        return DocumentChunk.builder()
                .id(UUID.randomUUID())
                .document(Document.builder().id(UUID.randomUUID()).build())
                .content(content)
                .chunkType(type)
                .build();
    }

    private static void setField(Object target, String name, Object value) {
        try {
            var field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
