package KLTN.RAG_CHATBOT_BE.service;

import KLTN.RAG_CHATBOT_BE.domain.chat.ChatMessage;
import KLTN.RAG_CHATBOT_BE.dto.RetrievedContext;
import KLTN.RAG_CHATBOT_BE.dto.TokenUsageDto;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Per-request token usage tracking (estimate + provider actual when available).
 * Does not log full prompt, API keys, or context bodies.
 */
@Slf4j
public final class RagTokenAudit {

    public static final String PROVIDER_GROQ = "GROQ";

    private static final Pattern PROVIDER_REQUESTED_TOKENS = Pattern.compile(
            "Requested\\s+(\\d+)\\s+tokens?",
            Pattern.CASE_INSENSITIVE
    );

    public enum Mode {
        CHAT,
        PLAYGROUND,
        COMPARE_A,
        COMPARE_B,
        WIDGET
    }

    private static final ThreadLocal<AuditState> STATE = new ThreadLocal<>();

    private RagTokenAudit() {}

    public static void begin(Mode mode, UUID chatbotId, String sessionId) {
        AuditState state = new AuditState();
        state.requestId = UUID.randomUUID().toString().substring(0, 8);
        state.mode = mode;
        state.chatbotId = chatbotId != null ? chatbotId.toString() : null;
        state.sessionId = sessionId;
        state.provider = PROVIDER_GROQ;
        STATE.set(state);
    }

    public static boolean hasActiveState() {
        return STATE.get() != null;
    }

    public static void incrementEmbeddingCalls() {
        AuditState state = STATE.get();
        if (state != null) {
            state.embeddingCalls++;
        }
    }

    public static void incrementRerankCalls() {
        AuditState state = STATE.get();
        if (state != null) {
            state.rerankCalls++;
        }
    }

    public static void incrementLlmCallIndex() {
        AuditState state = STATE.get();
        if (state != null) {
            state.llmCallIndex++;
        }
    }

    public static int currentLlmCallIndex() {
        AuditState state = STATE.get();
        return state != null ? state.llmCallIndex : 0;
    }

    public static void setResolvedModel(String model) {
        AuditState state = STATE.get();
        if (state != null && model != null && !model.isBlank()) {
            state.model = model;
        }
    }

    public static void markCompareMode(boolean compareMode) {
        AuditState state = STATE.get();
        if (state != null) {
            state.compareMode = compareMode;
        }
    }

    /**
     * Stores pre-LLM char breakdown and token estimates (does not emit final log).
     */
    public static void recordPreLlm(
            String model,
            double temperature,
            int maxTokens,
            int contextTopN,
            int finalContexts,
            String systemPrompt,
            List<ChatMessage> history,
            int contextChars,
            String question,
            String userPrompt
    ) {
        AuditState state = STATE.get();
        if (state == null) {
            return;
        }

        state.model = model;
        state.temperature = temperature;
        state.maxTokens = maxTokens;
        state.contextTopN = contextTopN;
        state.finalContexts = finalContexts;
        state.systemChars = systemPrompt != null ? systemPrompt.length() : 0;
        state.historyMessages = history != null ? history.size() : 0;
        state.historyChars = historyChars(history);
        state.contextChars = contextChars;
        state.questionChars = question != null ? question.length() : 0;
        state.promptChars = userPrompt != null ? userPrompt.length() : 0;
        state.estimatedInputTokens = estimateTokens(state.systemChars + state.promptChars);
        state.reservedOutputTokens = maxTokens;
        state.estimatedTotalRequestTokens = state.estimatedInputTokens + state.reservedOutputTokens;
    }

    /** @deprecated Prefer {@link #recordPreLlm}; kept for minimal call-site churn. */
    public static void logPromptAudit(
            String model,
            double temperature,
            int maxTokens,
            int contextTopN,
            int finalContexts,
            String systemPrompt,
            List<ChatMessage> history,
            int contextChars,
            String question,
            String userPrompt,
            boolean compareMode
    ) {
        recordPreLlm(model, temperature, maxTokens, contextTopN, finalContexts,
                systemPrompt, history, contextChars, question, userPrompt);
        AuditState state = STATE.get();
        if (state != null) {
            state.compareMode = compareMode;
        }
    }

    public static void recordActualFromResponse(Response<?> response) {
        if (response == null) {
            return;
        }
        recordActualUsage(response.tokenUsage());
    }

    public static void recordActualUsage(TokenUsage usage) {
        if (usage == null) {
            return;
        }
        recordActualUsage(usage.inputTokenCount(), usage.outputTokenCount(), usage.totalTokenCount());
    }

    public static void recordActualUsage(Integer promptTokens, Integer completionTokens, Integer totalTokens) {
        AuditState state = STATE.get();
        if (state == null) {
            return;
        }
        if (promptTokens != null) {
            state.actualPromptTokens = promptTokens;
        }
        if (completionTokens != null) {
            state.actualCompletionTokens = completionTokens;
        }
        if (totalTokens != null) {
            state.actualTotalTokens = totalTokens;
        }
    }

    public static void recordLlmFailure(Throwable error, String model, int fallbackAttempt) {
        AuditState state = STATE.get();
        if (state == null) {
            return;
        }
        state.success = false;
        state.fallbackAttempt = fallbackAttempt;
        if (model != null && !model.isBlank()) {
            state.model = model;
        }
        String message = chainMessage(error);
        if (message != null) {
            Integer requested = parseProviderRequestedTokens(message);
            if (requested != null) {
                state.providerRequestedTokens = requested;
            }
            if (message.contains("rate_limit_exceeded")) {
                state.errorCode = "rate_limit_exceeded";
                state.errorType = "tokens";
            } else if (message.contains("Rate limit")) {
                state.errorType = "rate_limit";
            } else {
                state.errorType = error != null ? error.getClass().getSimpleName() : "error";
            }
        }
    }

    /**
     * Emits {@code [RAG][token-usage]}, returns snapshot for API, clears ThreadLocal.
     */
    public static TokenUsageDto finish(boolean success) {
        AuditState state = STATE.get();
        if (state == null) {
            return null;
        }
        if (success) {
            state.success = true;
        }
        TokenUsageDto dto = state.toDto();
        logTokenUsage(state);
        STATE.remove();
        return dto;
    }

    public static void clear() {
        STATE.remove();
    }

    public static int estimateTokens(int chars) {
        if (chars <= 0) {
            return 0;
        }
        return (int) Math.ceil(chars / 4.0);
    }

    public static int estimatedTotalRequestTokens(int estimatedInputTokens, int reservedOutputTokens) {
        return estimatedInputTokens + reservedOutputTokens;
    }

    public static Integer parseProviderRequestedTokens(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }
        Matcher matcher = PROVIDER_REQUESTED_TOKENS.matcher(message);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    public static int contextCharsFromRetrieved(List<RetrievedContext> contexts) {
        if (contexts == null || contexts.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (RetrievedContext ctx : contexts) {
            if (ctx != null && ctx.getContent() != null) {
                total += ctx.getContent().length();
            }
        }
        return total;
    }

    public static int historyChars(List<ChatMessage> history) {
        if (history == null || history.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (ChatMessage msg : history) {
            if (msg != null && msg.getContent() != null) {
                total += msg.getContent().length();
            }
        }
        return total;
    }

    private static void logTokenUsage(AuditState state) {
        boolean compareMode = state.mode == Mode.COMPARE_A || state.mode == Mode.COMPARE_B || state.compareMode;
        log.info(
                "[RAG][token-usage] requestId={} mode={} chatbotId={} sessionId={} model={} provider={} "
                        + "temperature={} contextTopN={} finalContexts={} maxTokens={} "
                        + "systemChars={} historyChars={} contextChars={} questionChars={} promptChars={} "
                        + "estimatedInputTokens={} reservedOutputTokens={} estimatedTotalRequestTokens={} "
                        + "actualPromptTokens={} actualCompletionTokens={} actualTotalTokens={} "
                        + "providerRequestedTokens={} embeddingCalls={} rerankCalls={} llmCallIndex={} "
                        + "fallbackAttempt={} success={} errorType={} errorCode={} compareMode={}",
                state.requestId,
                state.mode,
                state.chatbotId,
                state.sessionId,
                nullToDash(state.model),
                nullToDash(state.provider),
                state.temperature,
                state.contextTopN,
                state.finalContexts,
                state.maxTokens,
                state.systemChars,
                state.historyChars,
                state.contextChars,
                state.questionChars,
                state.promptChars,
                state.estimatedInputTokens,
                state.reservedOutputTokens,
                state.estimatedTotalRequestTokens,
                formatNullable(state.actualPromptTokens),
                formatNullable(state.actualCompletionTokens),
                formatNullable(state.actualTotalTokens),
                formatNullable(state.providerRequestedTokens),
                state.embeddingCalls,
                state.rerankCalls,
                state.llmCallIndex,
                state.fallbackAttempt,
                state.success,
                nullToDash(state.errorType),
                nullToDash(state.errorCode),
                compareMode
        );
    }

    private static String chainMessage(Throwable error) {
        if (error == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        Throwable current = error;
        while (current != null) {
            if (current.getMessage() != null) {
                if (sb.length() > 0) {
                    sb.append(' ');
                }
                sb.append(current.getMessage());
            }
            current = current.getCause();
        }
        return sb.toString();
    }

    private static String formatNullable(Integer value) {
        return value == null ? "null" : String.valueOf(value);
    }

    private static String nullToDash(String value) {
        return value == null ? "-" : value;
    }

    private static final class AuditState {
        String requestId;
        Mode mode;
        String chatbotId;
        String sessionId;
        String provider = PROVIDER_GROQ;
        String model;
        double temperature;
        int maxTokens;
        int contextTopN;
        int finalContexts;
        int systemChars;
        int historyMessages;
        int historyChars;
        int contextChars;
        int questionChars;
        int promptChars;
        int estimatedInputTokens;
        int reservedOutputTokens;
        int estimatedTotalRequestTokens;
        Integer actualPromptTokens;
        Integer actualCompletionTokens;
        Integer actualTotalTokens;
        Integer providerRequestedTokens;
        int embeddingCalls;
        int rerankCalls;
        int llmCallIndex;
        int fallbackAttempt;
        boolean success = true;
        boolean compareMode;
        String errorType;
        String errorCode;

        TokenUsageDto toDto() {
            return TokenUsageDto.builder()
                    .estimatedInputTokens(estimatedInputTokens)
                    .reservedOutputTokens(reservedOutputTokens)
                    .estimatedTotalRequestTokens(estimatedTotalRequestTokens)
                    .actualPromptTokens(actualPromptTokens)
                    .actualCompletionTokens(actualCompletionTokens)
                    .actualTotalTokens(actualTotalTokens)
                    .providerRequestedTokens(providerRequestedTokens)
                    .systemChars(systemChars)
                    .historyChars(historyChars)
                    .contextChars(contextChars)
                    .questionChars(questionChars)
                    .promptChars(promptChars)
                    .historyMessages(historyMessages)
                    .finalContexts(finalContexts)
                    .contextTopN(contextTopN)
                    .embeddingCalls(embeddingCalls)
                    .rerankCalls(rerankCalls)
                    .llmCallIndex(llmCallIndex)
                    .model(model)
                    .provider(provider)
                    .requestId(requestId)
                    .compareMode(mode == Mode.COMPARE_A || mode == Mode.COMPARE_B || compareMode)
                    .success(success)
                    .errorType(errorType)
                    .errorCode(errorCode)
                    .build();
        }
    }
}
