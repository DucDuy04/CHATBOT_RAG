package KLTN.RAG_CHATBOT_BE.service;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChatServiceLlmParamsTest {

    @Test
    void resolveLlmGeneration_requestOverridesModelConfig() {
        ChatService.LlmGenerationResolution resolution = ChatService.resolveLlmGeneration(
                0.7, 512, 0.2, 256);
        assertEquals(ChatService.ConfigParamSource.REQUEST, resolution.temperatureSource());
        assertEquals(ChatService.ConfigParamSource.REQUEST, resolution.maxTokensSource());
        assertEquals(0.7, resolution.effective().temperature());
        assertEquals(512, resolution.effective().maxTokens());
    }

    @Test
    void resolveLlmGeneration_usesModelConfigWhenRequestNull() {
        ChatService.LlmGenerationResolution resolution = ChatService.resolveLlmGeneration(
                null, null, 0.2, 256);
        assertEquals(ChatService.ConfigParamSource.MODEL_CONFIG, resolution.temperatureSource());
        assertEquals(ChatService.ConfigParamSource.MODEL_CONFIG, resolution.maxTokensSource());
        assertEquals(0.2, resolution.effective().temperature());
        assertEquals(256, resolution.effective().maxTokens());
    }

    @Test
    void resolveLlmGeneration_defaultsWhenBothNull() {
        ChatService.LlmGenerationResolution resolution = ChatService.resolveLlmGeneration(
                null, null, null, null);
        assertEquals(ChatService.ConfigParamSource.DEFAULT, resolution.temperatureSource());
        assertEquals(ChatService.ConfigParamSource.DEFAULT, resolution.maxTokensSource());
        assertEquals(LlmGenerationOptions.DEFAULT_TEMPERATURE, resolution.effective().temperature());
        assertEquals(LlmGenerationOptions.DEFAULT_MAX_TOKENS, resolution.effective().maxTokens());
    }

    @Test
    void resolveLlmGeneration_clampsOutOfRange() {
        ChatService.LlmGenerationResolution resolution = ChatService.resolveLlmGeneration(
                999.0, 999_999, 0.2, 256);
        assertEquals(LlmGenerationOptions.MAX_TEMPERATURE, resolution.effective().temperature());
        assertEquals(LlmGenerationOptions.MAX_MAX_TOKENS, resolution.effective().maxTokens());
    }

    @Test
    void parseModelConfig_temperatureAndMaxTokens() {
        Map<String, Object> modelConfig = new java.util.LinkedHashMap<>();
        modelConfig.put("temperature", 0.3);
        modelConfig.put("maxTokens", 800);
        Map<String, Object> ui = new java.util.LinkedHashMap<>();
        ui.put("modelConfig", modelConfig);
        assertEquals(0.3, WidgetService.parseModelConfigTemperature(ui));
        assertEquals(800, WidgetService.parseModelConfigMaxTokens(ui));
    }

    @Test
    void adaptiveMaxTokens_factLikeUsesLowerCap() {
        int tokens = ChatService.resolveAdaptiveMaxTokens(
                "What room is ID ABC123 in?",
                QueryAnalyzerService.QueryType.NORMAL_FACT,
                768);
        assertEquals(384, tokens);
    }

    @Test
    void adaptiveMaxTokens_listLikeKeepsHigherCap() {
        int tokens = ChatService.resolveAdaptiveMaxTokens(
                "List all items in this section",
                QueryAnalyzerService.QueryType.LIST_ALL,
                768);
        assertEquals(768, tokens);
    }

    @Test
    void adaptiveMaxTokens_userUpperBoundIsNotExceeded() {
        int tokens = ChatService.resolveAdaptiveMaxTokens(
                "Which value matches ABC123?",
                QueryAnalyzerService.QueryType.TABLE_LOOKUP,
                256);
        assertEquals(256, tokens);
    }

    @Test
    void adaptiveMaxTokens_multiAttributeLookupUsesModerateCap() {
        int tokens = ChatService.resolveAdaptiveMaxTokens(
                "Which teacher, day, period, and room does group 2 use?",
                QueryAnalyzerService.QueryType.TABLE_LOOKUP,
                768);
        assertEquals(512, tokens);
    }
}
