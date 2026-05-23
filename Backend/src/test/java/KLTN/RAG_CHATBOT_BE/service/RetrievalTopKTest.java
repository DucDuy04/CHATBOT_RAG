package KLTN.RAG_CHATBOT_BE.service;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RetrievalTopKTest {

    @Test
    void normalizeFinalContextTopN_nullUsesDefault() {
        assertEquals(RagRetrievalService.DEFAULT_FINAL_CONTEXT_TOP_N,
                RagRetrievalService.normalizeFinalContextTopN(null));
    }

    @Test
    void normalizeFinalContextTopN_clampsBelowMin() {
        assertEquals(RagRetrievalService.MIN_FINAL_CONTEXT_TOP_N,
                RagRetrievalService.normalizeFinalContextTopN(0));
    }

    @Test
    void normalizeFinalContextTopN_clampsAboveMax() {
        assertEquals(RagRetrievalService.MAX_FINAL_CONTEXT_TOP_N,
                RagRetrievalService.normalizeFinalContextTopN(999));
    }

    @Test
    void normalizeFinalContextTopN_passesThroughInRange() {
        assertEquals(3, RagRetrievalService.normalizeFinalContextTopN(3));
        assertEquals(10, RagRetrievalService.normalizeFinalContextTopN(10));
    }

    @Test
    void fixedVectorAnchorK_ignoresUiTopK() {
        assertEquals(RagRetrievalService.DEFAULT_VECTOR_ANCHOR_K,
                RagRetrievalService.normalizeAnchorTopK(5));
        assertEquals(RagRetrievalService.DEFAULT_VECTOR_ANCHOR_K,
                RagRetrievalService.normalizeAnchorTopK(null));
    }

    @Test
    void parseTopKOverride_readsNumberAndString() {
        assertEquals(5, RagRetrievalService.parseTopKOverride(Map.of("topK", 5)));
        assertEquals(10, RagRetrievalService.parseTopKOverride(Map.of("topK", "10")));
    }

    @Test
    void parseTopKOverride_invalidReturnsNull() {
        assertNull(RagRetrievalService.parseTopKOverride(Map.of("topK", "abc")));
        assertNull(RagRetrievalService.parseTopKOverride(null));
    }
}
