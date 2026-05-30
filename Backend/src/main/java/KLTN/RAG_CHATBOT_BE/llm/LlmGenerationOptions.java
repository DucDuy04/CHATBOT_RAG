package KLTN.RAG_CHATBOT_BE.llm;

import java.util.Map;

/**
 * Per-request LLM generation parameters (temperature, max output tokens).
 * Immutable after normalization — safe to pass through services without global state.
 */
public record LlmGenerationOptions(double temperature, int maxTokens) {

  /** Matches {@link LlmFallbackService} non-streaming builder defaults before overrides. */
  public static final double DEFAULT_TEMPERATURE = 0.1;

  public static final int DEFAULT_MAX_TOKENS = 1500;

  public static final double MIN_TEMPERATURE = 0.0;

  public static final double MAX_TEMPERATURE = 1.0;

  public static final int MIN_MAX_TOKENS = 64;

  public static final int MAX_MAX_TOKENS = 4096;

  public static LlmGenerationOptions defaults() {
    return new LlmGenerationOptions(DEFAULT_TEMPERATURE, DEFAULT_MAX_TOKENS);
  }

  public static LlmGenerationOptions normalized(Double temperature, Integer maxTokens) {
    return new LlmGenerationOptions(
        normalizeTemperature(temperature),
        normalizeMaxTokens(maxTokens));
  }

  public static double normalizeTemperature(Double value) {
    if (value == null) {
      return DEFAULT_TEMPERATURE;
    }
    return Math.max(MIN_TEMPERATURE, Math.min(MAX_TEMPERATURE, value));
  }

  public static int normalizeMaxTokens(Integer value) {
    if (value == null) {
      return DEFAULT_MAX_TOKENS;
    }
    return Math.max(MIN_MAX_TOKENS, Math.min(MAX_MAX_TOKENS, value));
  }

  public static Double parseTemperatureOverride(Map<String, Object> params) {
    return parseDoubleParam(params, "temperature");
  }

  public static Integer parseMaxTokensOverride(Map<String, Object> params) {
    return parseIntParam(params, "maxTokens");
  }

  private static Double parseDoubleParam(Map<String, Object> params, String key) {
    if (params == null || params.isEmpty()) {
      return null;
    }
    Object raw = params.get(key);
    if (raw == null) {
      return null;
    }
    if (raw instanceof Number number) {
      return number.doubleValue();
    }
    if (raw instanceof String text) {
      String trimmed = text.trim();
      if (trimmed.isEmpty()) {
        return null;
      }
      try {
        return Double.parseDouble(trimmed);
      } catch (NumberFormatException ignored) {
        return null;
      }
    }
    return null;
  }

  private static Integer parseIntParam(Map<String, Object> params, String key) {
    if (params == null || params.isEmpty()) {
      return null;
    }
    Object raw = params.get(key);
    if (raw == null) {
      return null;
    }
    if (raw instanceof Number number) {
      return number.intValue();
    }
    if (raw instanceof String text) {
      String trimmed = text.trim();
      if (trimmed.isEmpty()) {
        return null;
      }
      try {
        return Integer.parseInt(trimmed);
      } catch (NumberFormatException ignored) {
        return null;
      }
    }
    return null;
  }
}
