package KLTN.RAG_CHATBOT_BE.service;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ChatServiceModelConfigTopKTest {

    @Test
    void resolveTopK_requestOverridesModelConfig() {
        ChatService.TopKResolution resolution = ChatService.resolveTopK(10, 5);
        assertEquals(ChatService.TopKSource.REQUEST, resolution.source());
        assertEquals(10, resolution.effective());
    }

    @Test
    void resolveTopK_usesModelConfigWhenRequestNull() {
        ChatService.TopKResolution resolution = ChatService.resolveTopK(null, 5);
        assertEquals(ChatService.TopKSource.MODEL_CONFIG, resolution.source());
        assertEquals(5, resolution.effective());
    }

    @Test
    void resolveTopK_backendDefaultWhenBothNull() {
        ChatService.TopKResolution resolution = ChatService.resolveTopK(null, null);
        assertEquals(ChatService.TopKSource.DEFAULT, resolution.source());
        assertEquals(RagRetrievalService.DEFAULT_FINAL_CONTEXT_TOP_N, resolution.effective());
    }

    @Test
    void resolveTopK_clampsRequestAboveMax() {
        ChatService.TopKResolution resolution = ChatService.resolveTopK(999, 5);
        assertEquals(ChatService.TopKSource.REQUEST, resolution.source());
        assertEquals(RagRetrievalService.MAX_FINAL_CONTEXT_TOP_N, resolution.effective());
    }

    @Test
    void resolveTopK_clampsModelConfigAboveMax() {
        ChatService.TopKResolution resolution = ChatService.resolveTopK(null, 999);
        assertEquals(ChatService.TopKSource.MODEL_CONFIG, resolution.source());
        assertEquals(RagRetrievalService.MAX_FINAL_CONTEXT_TOP_N, resolution.effective());
    }

    @Test
    void resolveTopK_clampsModelConfigBelowMin() {
        ChatService.TopKResolution resolution = ChatService.resolveTopK(null, 0);
        assertEquals(ChatService.TopKSource.MODEL_CONFIG, resolution.source());
        assertEquals(RagRetrievalService.MIN_FINAL_CONTEXT_TOP_N, resolution.effective());
    }

    @Test
    void parseModelConfigTopK_readsFromUiConfig() {
        Map<String, Object> uiConfig = Map.of(
                "modelConfig", Map.of("topK", 5)
        );
        assertEquals(5, WidgetService.parseModelConfigTopK(uiConfig));
    }

    @Test
    void parseModelConfigTopK_absentReturnsNull() {
        assertNull(WidgetService.parseModelConfigTopK(Map.of()));
        assertNull(WidgetService.parseModelConfigTopK(null));
    }

    @Test
    void parseModelConfigTopK_invalidReturnsNull() {
        Map<String, Object> uiConfig = Map.of(
                "modelConfig", Map.of("topK", "not-a-number")
        );
        assertNull(WidgetService.parseModelConfigTopK(uiConfig));
    }
}
