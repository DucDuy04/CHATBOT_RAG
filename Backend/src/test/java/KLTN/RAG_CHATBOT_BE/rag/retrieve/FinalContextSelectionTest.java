package KLTN.RAG_CHATBOT_BE.rag.retrieve;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FinalContextSelectionTest {

    @Test
    void normalizeFinalContextTopN_clampsToRange() {
        assertEquals(10, RagRetrievalService.normalizeFinalContextTopN(null));
        assertEquals(1, RagRetrievalService.normalizeFinalContextTopN(0));
        assertEquals(30, RagRetrievalService.normalizeFinalContextTopN(100));
        assertEquals(15, RagRetrievalService.normalizeFinalContextTopN(15));
    }

    @Test
    void resolveFinalContextTopN_overrideWins() {
        assertEquals(12, RagRetrievalService.resolveFinalContextTopN(
                12, KLTN.RAG_CHATBOT_BE.rag.analysis.QueryAnalyzerService.QueryType.NORMAL_FACT));
    }

    @Test
    void resolveFinalContextTopN_tableLookupDefault() {
        assertEquals(10, RagRetrievalService.resolveFinalContextTopN(
                null, KLTN.RAG_CHATBOT_BE.rag.analysis.QueryAnalyzerService.QueryType.TABLE_LOOKUP));
    }

    @Test
    void resolveFinalContextTopN_listAllDefaultHigher() {
        assertEquals(20, RagRetrievalService.resolveFinalContextTopN(
                null, KLTN.RAG_CHATBOT_BE.rag.analysis.QueryAnalyzerService.QueryType.LIST_ALL));
    }

    @Test
    void resolveAdaptiveFinalContextTopN_listLikeQuestion_reducesBelowOverride() {
        int topN = RagRetrievalService.resolveAdaptiveFinalContextTopN(
                "Liệt kê tất cả các mục trong tài liệu",
                20,
                KLTN.RAG_CHATBOT_BE.rag.analysis.QueryAnalyzerService.QueryType.LIST_ALL);
        assertTrue(topN <= 15);
        assertTrue(topN >= RagRetrievalService.MIN_FINAL_CONTEXT_TOP_N);
    }

    @Test
    void resolveAdaptiveFinalContextTopN_compareLikeQuestion_capsAdaptive() {
        int topN = RagRetrievalService.resolveAdaptiveFinalContextTopN(
                "So sánh phương án A và phương án B",
                20,
                KLTN.RAG_CHATBOT_BE.rag.analysis.QueryAnalyzerService.QueryType.TABLE_LOOKUP);
        assertEquals(10, topN);
    }

    @Test
    void adaptiveTopNReason_detectsListLike() {
        assertEquals("list_like", RagRetrievalService.adaptiveTopNReason(
                "Liệt kê tất cả", KLTN.RAG_CHATBOT_BE.rag.analysis.QueryAnalyzerService.QueryType.NORMAL_FACT));
    }

    @Test
    void adaptiveTopNReason_detectsCompareLike() {
        assertEquals("compare_like", RagRetrievalService.adaptiveTopNReason(
                "So sánh A và B", KLTN.RAG_CHATBOT_BE.rag.analysis.QueryAnalyzerService.QueryType.TABLE_LOOKUP));
    }

    @Test
    void parseTopKOverride_readsIntegerFromMap() {
        assertEquals(7, RagRetrievalService.parseTopKOverride(java.util.Map.of("topK", 7)));
        assertEquals(8, RagRetrievalService.parseTopKOverride(java.util.Map.of("topK", "8")));
        assertNull(RagRetrievalService.parseTopKOverride(java.util.Map.of()));
        assertNull(RagRetrievalService.parseTopKOverride(null));
    }
}
