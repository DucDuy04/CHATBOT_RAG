package KLTN.RAG_CHATBOT_BE.rag.runtime;

import KLTN.RAG_CHATBOT_BE.rag.retrieve.RagRetrievalService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RetrievalTopKTest {

    @Test
    void normalizeAnchorTopK_returnsFixedVectorAnchor() {
        assertEquals(RagRetrievalService.DEFAULT_VECTOR_ANCHOR_K,
                RagRetrievalService.normalizeAnchorTopK(999));
    }

    @Test
    void finalContextBounds_areConsistent() {
        assertTrue(RagRetrievalService.MIN_FINAL_CONTEXT_TOP_N < RagRetrievalService.MAX_FINAL_CONTEXT_TOP_N);
        assertEquals(RagRetrievalService.MIN_FINAL_CONTEXT_TOP_N,
                RagRetrievalService.normalizeFinalContextTopN(0));
    }

    @Test
    void resolveTopK_requestOverridesConfig() {
        ChatService.TopKResolution resolution = ChatService.resolveTopK(12, 8);
        assertEquals(ChatService.TopKSource.REQUEST, resolution.source());
        assertEquals(12, resolution.effective());
    }

    @Test
    void resolveTopK_modelConfigWhenNoRequest() {
        ChatService.TopKResolution resolution = ChatService.resolveTopK(null, 8);
        assertEquals(ChatService.TopKSource.MODEL_CONFIG, resolution.source());
        assertEquals(8, resolution.effective());
    }
}
