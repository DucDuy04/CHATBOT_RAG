package KLTN.RAG_CHATBOT_BE.service;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RetrievalTopKTest {

    @Test
    void normalizeAnchorTopK_nullUsesDefault() {
        assertEquals(RagRetrievalService.DEFAULT_ANCHOR_TOP_K, RagRetrievalService.normalizeAnchorTopK(null));
    }

    @Test
    void normalizeAnchorTopK_clampsBelowMin() {
        assertEquals(RagRetrievalService.MIN_ANCHOR_TOP_K, RagRetrievalService.normalizeAnchorTopK(0));
    }

    @Test
    void normalizeAnchorTopK_clampsAboveMax() {
        assertEquals(RagRetrievalService.MAX_ANCHOR_TOP_K, RagRetrievalService.normalizeAnchorTopK(999));
    }

    @Test
    void normalizeAnchorTopK_passesThroughInRange() {
        assertEquals(3, RagRetrievalService.normalizeAnchorTopK(3));
        assertEquals(10, RagRetrievalService.normalizeAnchorTopK(10));
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
