package KLTN.RAG_CHATBOT_BE.service;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LlmGenerationOptionsTest {

    @Test
    void normalizeTemperature_defaultsWhenNull() {
        assertEquals(LlmGenerationOptions.DEFAULT_TEMPERATURE, LlmGenerationOptions.normalizeTemperature(null));
    }

    @Test
    void normalizeTemperature_clampsHigh() {
        assertEquals(LlmGenerationOptions.MAX_TEMPERATURE, LlmGenerationOptions.normalizeTemperature(999.0));
    }

    @Test
    void normalizeTemperature_clampsLow() {
        assertEquals(LlmGenerationOptions.MIN_TEMPERATURE, LlmGenerationOptions.normalizeTemperature(-1.0));
    }

    @Test
    void normalizeMaxTokens_defaultsWhenNull() {
        assertEquals(LlmGenerationOptions.DEFAULT_MAX_TOKENS, LlmGenerationOptions.normalizeMaxTokens(null));
    }

    @Test
    void normalizeMaxTokens_clampsHigh() {
        assertEquals(LlmGenerationOptions.MAX_MAX_TOKENS, LlmGenerationOptions.normalizeMaxTokens(999_999));
    }

    @Test
    void normalizeMaxTokens_clampsLow() {
        assertEquals(LlmGenerationOptions.MIN_MAX_TOKENS, LlmGenerationOptions.normalizeMaxTokens(10));
    }

    @Test
    void parseOverride_readsNumberAndString() {
        assertEquals(0.7, LlmGenerationOptions.parseTemperatureOverride(Map.of("temperature", 0.7)));
        assertEquals(512, LlmGenerationOptions.parseMaxTokensOverride(Map.of("maxTokens", "512")));
    }

    @Test
    void parseOverride_invalidReturnsNull() {
        assertNull(LlmGenerationOptions.parseTemperatureOverride(Map.of("temperature", "bad")));
        assertNull(LlmGenerationOptions.parseMaxTokensOverride(null));
    }
}
