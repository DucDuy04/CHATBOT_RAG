package KLTN.RAG_CHATBOT_BE.service;

import KLTN.RAG_CHATBOT_BE.domain.document.Document;
import KLTN.RAG_CHATBOT_BE.domain.document.DocumentChunk;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Task 23G — Top-K as final context top-N after rerank/score selection.
 */
class FinalContextSelectionTest {

    private static final Document SHARED_DOC = Document.builder().id(UUID.randomUUID()).build();

    @Test
    void topKControlsFinalContextCount() {
        List<RagRetrievalService.ScoredChunk> scored = buildScoredPool(25, 0.5);
        List<DocumentChunk> selected = RagRetrievalService.selectTopNByScore(scored, 5, 32000);
        assertTrue(selected.size() <= 5);
    }

    @Test
    void rerankBeforeBudget_selectsHighScoreLateDocumentChunks() {
        List<RagRetrievalService.ScoredChunk> scored = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            double score = i < 20 ? 0.1 : 1.0;
            scored.add(new RagRetrievalService.ScoredChunk(mockChunk(i, "sec_" + i), score));
        }
        scored.sort((a, b) -> Double.compare(b.finalScore(), a.finalScore()));

        List<DocumentChunk> selected = RagRetrievalService.selectTopNByScore(scored, 5, 32000);
        assertEquals(5, selected.size());
        assertTrue(selected.stream().anyMatch(c -> c.getSectionId().equals("sec_24")));
        assertTrue(selected.stream().anyMatch(c -> c.getSectionId().equals("sec_20")));
    }

    @Test
    void documentOrderAfterSelection() {
        List<DocumentChunk> selected = List.of(
                mockChunk(30, "sec_c"),
                mockChunk(10, "sec_a"),
                mockChunk(20, "sec_b")
        );
        List<DocumentChunk> ordered = RagRetrievalService.sortByDocumentOrder(selected, false);
        assertEquals("sec_a", ordered.get(0).getSectionId());
        assertEquals("sec_b", ordered.get(1).getSectionId());
        assertEquals("sec_c", ordered.get(2).getSectionId());
    }

    @Test
    void fixedVectorAnchorK_unaffectedByUiTopK() {
        assertEquals(RagRetrievalService.DEFAULT_VECTOR_ANCHOR_K,
                RagRetrievalService.normalizeAnchorTopK(1));
        assertEquals(RagRetrievalService.DEFAULT_VECTOR_ANCHOR_K,
                RagRetrievalService.normalizeAnchorTopK(20));
        assertEquals(1, RagRetrievalService.normalizeFinalContextTopN(1));
        assertEquals(20, RagRetrievalService.normalizeFinalContextTopN(20));
    }

    @Test
    void noFinalContextSelectorOrBudgetStopwords() {
        assertThrows(ClassNotFoundException.class, () ->
                Class.forName("KLTN.RAG_CHATBOT_BE.service.FinalContextSelector"));
    }

    @Test
    void resolveFinalContextTopN_userOverrideBeatsQueryTypeDefault() {
        int n = RagRetrievalService.resolveFinalContextTopN(
                5, QueryAnalyzerService.QueryType.LIST_ALL);
        assertEquals(5, n);
    }

    @Test
    void resolveFinalContextTopN_queryTypeDefaultWhenNull() {
        int listAll = RagRetrievalService.resolveFinalContextTopN(
                null, QueryAnalyzerService.QueryType.LIST_ALL);
        assertEquals(20, listAll);
        int normal = RagRetrievalService.resolveFinalContextTopN(
                null, QueryAnalyzerService.QueryType.NORMAL_FACT);
        assertEquals(RagRetrievalService.DEFAULT_FINAL_CONTEXT_TOP_N, normal);
    }

    private static List<RagRetrievalService.ScoredChunk> buildScoredPool(int size, double baseScore) {
        List<RagRetrievalService.ScoredChunk> scored = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            scored.add(new RagRetrievalService.ScoredChunk(
                    mockChunk(i, "sec_" + i), baseScore + i * 0.01));
        }
        scored.sort((a, b) -> Double.compare(b.finalScore(), a.finalScore()));
        return scored;
    }

    private static DocumentChunk mockChunk(int orderIndex, String sectionId) {
        return DocumentChunk.builder()
                .id(UUID.randomUUID())
                .document(SHARED_DOC)
                .orderIndex(orderIndex)
                .sectionOrder(orderIndex)
                .sectionId(sectionId)
                .content("content-" + sectionId)
                .chunkType("text")
                .build();
    }
}
