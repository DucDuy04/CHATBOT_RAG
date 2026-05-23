package KLTN.RAG_CHATBOT_BE.dto;

import lombok.Builder;
import lombok.Value;

/**
 * Per-request token usage snapshot for Playground debug and audit logs.
 * Estimated fields always set; actual fields null when provider omits usage.
 */
@Value
@Builder
public class TokenUsageDto {
    Integer estimatedInputTokens;
    Integer reservedOutputTokens;
    Integer estimatedTotalRequestTokens;
    Integer actualPromptTokens;
    Integer actualCompletionTokens;
    Integer actualTotalTokens;
    Integer providerRequestedTokens;

    Integer systemChars;
    Integer historyChars;
    Integer contextChars;
    Integer questionChars;
    Integer promptChars;
    Integer historyMessages;

    Integer finalContexts;
    Integer contextTopN;
    Integer embeddingCalls;
    Integer rerankCalls;
    Integer llmCallIndex;

    String model;
    String provider;
    String requestId;
    Boolean compareMode;
    Boolean success;
    String errorType;
    String errorCode;
}
