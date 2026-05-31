package KLTN.RAG_CHATBOT_BE.rag.runtime;

import KLTN.RAG_CHATBOT_BE.domain.chat.ChatMessage;
import KLTN.RAG_CHATBOT_BE.domain.chat.ChatMessageRepository;
import KLTN.RAG_CHATBOT_BE.domain.chat.ChatSession;
import KLTN.RAG_CHATBOT_BE.domain.chat.ChatSessionRepository;
import KLTN.RAG_CHATBOT_BE.domain.enums.MessageRole;
import KLTN.RAG_CHATBOT_BE.domain.widget.WidgetConfig;
import KLTN.RAG_CHATBOT_BE.domain.widget.WidgetConfigRepository;
import KLTN.RAG_CHATBOT_BE.dto.ChatRequest;
import KLTN.RAG_CHATBOT_BE.dto.ChatResponse;
import KLTN.RAG_CHATBOT_BE.dto.RetrievedContext;
import KLTN.RAG_CHATBOT_BE.dto.TokenUsageDto;
import KLTN.RAG_CHATBOT_BE.audit.metrics.RagLatencyTrace;
import KLTN.RAG_CHATBOT_BE.audit.metrics.RagTokenAudit;
import KLTN.RAG_CHATBOT_BE.rag.prompt.PromptBuilderService;
import KLTN.RAG_CHATBOT_BE.rag.retrieve.CellAwareTableRowScorer;
import KLTN.RAG_CHATBOT_BE.rag.retrieve.RagRetrievalService;
import KLTN.RAG_CHATBOT_BE.rag.analysis.QueryAnalysisResult;
import KLTN.RAG_CHATBOT_BE.rag.analysis.QueryAnalyzerService;
import KLTN.RAG_CHATBOT_BE.rag.analysis.QuerySignalExtractor;
import KLTN.RAG_CHATBOT_BE.llm.LlmFallbackService;
import KLTN.RAG_CHATBOT_BE.llm.LlmGenerationOptions;
import KLTN.RAG_CHATBOT_BE.service.WidgetService;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.model.output.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    /** Max sources returned to client (API/SSE); does not limit LLM retrieval context. */
    static final int MAX_RESPONSE_SOURCES = 5;

    /** Tighter cap when answer is a clear no-info / out-of-scope refusal. */
    static final int MAX_REFUSAL_RESPONSE_SOURCES = 2;

    enum ConfigParamSource {
        REQUEST,
        MODEL_CONFIG,
        DEFAULT
    }

    enum TopKSource {
        REQUEST,
        MODEL_CONFIG,
        DEFAULT
    }

    record TopKResolution(TopKSource source, Integer requested, Integer configured, Integer candidate) {
        /** Resolved final context top-N (UI field {@code topK}). */
        int effective() {
            return RagRetrievalService.normalizeFinalContextTopN(candidate);
        }
    }

    record LlmGenerationResolution(
            ConfigParamSource temperatureSource,
            ConfigParamSource maxTokensSource,
            Double requestedTemperature,
            Double configuredTemperature,
            Integer requestedMaxTokens,
            Integer configuredMaxTokens,
            LlmGenerationOptions effective
    ) {}

    private static final int CONTENT_PREVIEW_DEDUP_CHARS = 120;

    private static final List<String> REFUSAL_ANSWER_MARKERS = List.of(
            "không tìm thấy thông tin này trong tài liệu",
            "không có trong tài liệu",
            "không tìm thấy thông tin",
            "không có thông tin"
    );

    /** Policy-style codes in answers (e.g. ALPHA-111) indicate factual partial responses. */
    private static final Pattern FACTUAL_POLICY_CODE = Pattern.compile("\\b[A-Z][A-Z0-9]*-\\d+\\b");

    private static final Pattern NUMBERED_LIST_ITEM = Pattern.compile("(?m)^\\s*\\d+\\.\\s+\\S");

    private final PromptBuilderService promptBuilderService;
    private final OpenAiChatModel chatModel;
    private final LlmFallbackService llmFallbackService;

    private final ChatMessageRepository chatMessageRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final WidgetConfigRepository widgetConfigRepository;

    private final RagRetrievalService ragRetrievalService;
    private final QueryAnalyzerService queryAnalyzerService;

    @Autowired
    @Qualifier("streamingExecutor")
    private Executor streamingExecutor;

    /** Injected only when {@code rag.runtime.async-persist.enabled=true}. */
    @Autowired(required = false)
    @Qualifier("chatPersistExecutor")
    private Executor chatPersistExecutor;

    @Value("${rag.runtime.async-persist.enabled:false}")
    private boolean asyncPersistEnabled;

    @Value("${rag.runtime.async-persist.log-payload-size:false}")
    private boolean logPersistPayloadSize;

    @Value("${groq.api-key}")
    private String groqApiKey;

    @Value("${groq.chat-model}")
    private String groqChatModel;

    @Value("${groq.base-url}")
    private String groqBaseUrl;

    public ChatResponse chat(ChatRequest request, UUID widgetId) {
        String question = request.getMessage();
        RagLatencyTrace latencyTrace = RagLatencyTrace.begin();

        ChatSession session = getOrCreateSession(request.getSessionId(), widgetId);
        RagTokenAudit.Mode auditMode = resolveAuditMode(request);
        RagTokenAudit.begin(auditMode, widgetId, session.getSessionKey().toString());

        try {
        log.info("[Chat] Nhận câu hỏi session={}, widgetId={}: {}", session.getId(), widgetId, question);

        saveChatMessage(session, MessageRole.USER, question, null);

        TopKResolution topKResolution = resolveRetrievalTopK(widgetId, request.getTopK());
        RagRetrievalService.RetrievalResult retrievalResult =
                ragRetrievalService.retrieveWithMetadata(question, widgetId, topKResolution.candidate());
        QueryAnalyzerService.QueryType queryType = queryTypeFromRetrievalResult(retrievalResult);
        List<RetrievedContext> contexts = retrievalResult.contexts();
        String lockedScopeLabel = retrievalResult.lockedScopeLabel();

        log.info("[Chat] Retrieval expanded được {} contexts cho widgetId={} lockedScope={}",
                contexts.size(), widgetId, lockedScopeLabel != null ? lockedScopeLabel : "none");

        long sourceStart = RagLatencyTrace.now();
        List<ChatResponse.SourceDto> sources = buildSourceDtosForResponse(contexts);
        latencyTrace.addSourceMs(RagLatencyTrace.elapsedMs(sourceStart));

        boolean hasTableLikeChunk = contexts.stream()
                .anyMatch(ctx -> "text_table_like".equals(ctx.getChunkType()));

        String queryTypeHint;
        if (hasTableLikeChunk) {
            if (queryType == QueryAnalyzerService.QueryType.TABLE_LOOKUP) {
                queryTypeHint = "TABLE_LOOKUP";
            } else {
                queryTypeHint = "TABLE_LIKE";
                log.info("[Chat] text_table_like chunk detected → overriding queryTypeHint to TABLE_LIKE");
            }
        } else {
            queryTypeHint = queryType.name();
        }

        String answer;

        if (contexts.isEmpty()) {
            answer = "Tôi không tìm thấy thông tin này trong tài liệu.";
            recordPreLlmMetrics(
                    request, topKResolution, contexts, List.of(), question, "", promptBuilderService.getSystemPrompt(),
                    resolveLlmGenerationOptions(widgetId, request, question, queryType));
            RagTokenAudit.finish(true);
        } else {
            List<ChatMessage> chatHistory =
                    chatMessageRepository.findTop10BySessionIdOrderByCreatedAtAsc(session.getId());

            long promptStart = RagLatencyTrace.now();
            String userPrompt = promptBuilderService.buildUserPromptFromRetrievedContexts(
                    question,
                    contexts,
                    chatHistory,
                    queryTypeHint,
                    lockedScopeLabel
            );
            latencyTrace.addPromptBuildMs(RagLatencyTrace.elapsedMs(promptStart));

            String systemPrompt = promptBuilderService.getSystemPrompt();

            List<dev.langchain4j.data.message.ChatMessage> messages = List.of(
                    SystemMessage.from(systemPrompt),
                    UserMessage.from(userPrompt)
            );

            LlmGenerationResolution llmOptions = resolveLlmGenerationOptions(widgetId, request, question, queryType);
            recordPreLlmMetrics(
                    request, topKResolution, contexts, chatHistory, question, userPrompt, systemPrompt, llmOptions);
            long llmStart = RagLatencyTrace.now();
            answer = llmFallbackService.generateWithFallback(messages, llmOptions.effective());
            latencyTrace.addLlmTotalMs(RagLatencyTrace.elapsedMs(llmStart));
            latencyTrace.setOutputTokens(estimateTokens(answer));
            RagTokenAudit.finish(!isOverloadAnswer(answer));
        }

        sourceStart = RagLatencyTrace.now();
        sources = applyAnswerAwareSourceCap(answer, sources, request);
        latencyTrace.addSourceMs(RagLatencyTrace.elapsedMs(sourceStart));

        saveChatMessage(session, MessageRole.ASSISTANT, answer, sources);

        return ChatResponse.builder()
                .answer(answer)
                .sources(sources)
                .build();
        } finally {
            if (RagTokenAudit.hasActiveState()) {
                RagTokenAudit.finish(false);
            }
            latencyTrace.close();
        }
    }

    public SseEmitter chatStream(ChatRequest request, UUID widgetId) {
        SseEmitter emitter = new SseEmitter(600_000L);
        String question = request.getMessage();

        ChatSession session = getOrCreateSession(request.getSessionId(), widgetId);
        RagTokenAudit.Mode streamAuditMode = resolveAuditMode(request);

        streamingExecutor.execute(() -> {
            RagLatencyTrace latencyTrace = RagLatencyTrace.begin();
            long sseStart = RagLatencyTrace.now();
            RagTokenAudit.begin(streamAuditMode, widgetId, session.getSessionKey().toString());
            AtomicBoolean tokenUsageFinished = new AtomicBoolean(false);
            AtomicBoolean asyncLlmStarted = new AtomicBoolean(false);
            AtomicBoolean latencyClosed = new AtomicBoolean(false);
            Runnable closeLatency = () -> {
                if (latencyClosed.compareAndSet(false, true)) {
                    latencyTrace.setSseTotalMs(RagLatencyTrace.elapsedMs(sseStart));
                    latencyTrace.close();
                }
            };
            try {
                log.info("[Stream] Nhận câu hỏi session={}, widgetId={}: {}", session.getId(), widgetId, question);

                saveChatMessage(session, MessageRole.USER, question, null);

                TopKResolution streamTopK = resolveRetrievalTopK(widgetId, request.getTopK());
                RagRetrievalService.RetrievalResult streamResult =
                        ragRetrievalService.retrieveWithMetadata(question, widgetId, streamTopK.candidate());
                QueryAnalyzerService.QueryType streamQueryType = queryTypeFromRetrievalResult(streamResult);
                List<RetrievedContext> contexts = streamResult.contexts();
                String streamLockedScope = streamResult.lockedScopeLabel();

                log.info("[Stream] Retrieval expanded được {} contexts cho widgetId={} lockedScope={}",
                        contexts.size(), widgetId, streamLockedScope != null ? streamLockedScope : "none");

                int sourcePresentationCap = resolveSourcePresentationCap(request, streamTopK);
                long sourceStart = RagLatencyTrace.now();
                List<ChatResponse.SourceDto> sources = buildSourceDtosForResponse(contexts, sourcePresentationCap);
                latencyTrace.addSourceMs(RagLatencyTrace.elapsedMs(sourceStart));

                boolean streamHasTableLikeChunk = contexts.stream()
                        .anyMatch(ctx -> "text_table_like".equals(ctx.getChunkType()));

                String streamQueryTypeHint;
                if (streamHasTableLikeChunk) {
                    if (streamQueryType == QueryAnalyzerService.QueryType.TABLE_LOOKUP) {
                        streamQueryTypeHint = "TABLE_LOOKUP";
                    } else {
                        streamQueryTypeHint = "TABLE_LIKE";
                        log.info("[Chat] text_table_like chunk detected → overriding queryTypeHint to TABLE_LIKE");
                    }
                } else {
                    streamQueryTypeHint = streamQueryType.name();
                }

                if (contexts.isEmpty()) {
                    String noContext = "Tôi không tìm thấy thông tin này trong tài liệu.";
                    LlmGenerationResolution emptyLlmOptions = resolveLlmGenerationOptions(widgetId, request, question, streamQueryType);
                    recordPreLlmMetrics(
                            request, streamTopK, contexts, List.of(), question, "", promptBuilderService.getSystemPrompt(), emptyLlmOptions);
                    TokenUsageDto emptyUsage = RagTokenAudit.finish(true);
                    tokenUsageFinished.set(true);

                    emitter.send(
                            SseEmitter.event()
                                    .name("token")
                                    .data("{\"token\":\"" + escapeJson(noContext) + "\"}", MediaType.APPLICATION_JSON)
                    );

                    emitter.send(
                            SseEmitter.event()
                                    .name("done")
                                    .data(buildStreamDonePayload(List.of(), emptyUsage, request),
                                            playgroundDoneMediaType(request))
                    );

                    emitter.complete();

                    saveChatMessage(session, MessageRole.ASSISTANT, noContext, null);
                    closeLatency.run();
                    return;
                }

                List<ChatMessage> chatHistory =
                        chatMessageRepository.findTop10BySessionIdOrderByCreatedAtAsc(session.getId());

                long promptStart = RagLatencyTrace.now();
                String userPrompt = promptBuilderService.buildUserPromptFromRetrievedContexts(
                        question,
                        contexts,
                        chatHistory,
                        streamQueryTypeHint,
                        streamLockedScope
                );
                latencyTrace.addPromptBuildMs(RagLatencyTrace.elapsedMs(promptStart));

                String systemPrompt = promptBuilderService.getSystemPrompt();

                List<dev.langchain4j.data.message.ChatMessage> messages = new ArrayList<>();
                messages.add(SystemMessage.from(systemPrompt));
                messages.add(UserMessage.from(userPrompt));

                StringBuilder fullAnswer = new StringBuilder();
                // Guard: đảm bảo emitter chỉ được complete 1 lần khi fallback xảy ra
                AtomicBoolean emitterDone = new AtomicBoolean(false);

                LlmGenerationResolution streamLlmOptions = resolveLlmGenerationOptions(widgetId, request, question, streamQueryType);
                recordPreLlmMetrics(
                        request, streamTopK, contexts, chatHistory, question, userPrompt, systemPrompt, streamLlmOptions);
                RagTokenAudit.incrementLlmCallIndex();
                long llmStart = RagLatencyTrace.now();
                AtomicBoolean firstTokenSeen = new AtomicBoolean(false);
                OpenAiStreamingChatModel streamingModel =
                        llmFallbackService.buildStreamingModel(groqChatModel, streamLlmOptions.effective());

                asyncLlmStarted.set(true);
                streamingModel.generate(messages, new StreamingResponseHandler<AiMessage>() {
                    @Override
                    public void onNext(String token) {
                        try {
                            if (firstTokenSeen.compareAndSet(false, true)) {
                                latencyTrace.addLlmFirstTokenMs(RagLatencyTrace.elapsedMs(llmStart));
                            }
                            fullAnswer.append(token);
                            emitter.send(
                                    SseEmitter.event()
                                            .name("token")
                                            .data("{\"token\":\"" + escapeJson(token) + "\"}", MediaType.APPLICATION_JSON)
                            );
                        } catch (IOException e) {
                            log.error("[Stream] Lỗi khi gửi token SSE: {}", e.getMessage(), e);
                            emitter.completeWithError(e);
                        }
                    }

                    @Override
                    public void onComplete(Response<AiMessage> response) {
                        if (!emitterDone.compareAndSet(false, true)) return;
                        try {
                            RagTokenAudit.recordActualFromResponse(response);
                            RagTokenAudit.setResolvedModel(groqChatModel);
                            latencyTrace.addLlmTotalMs(RagLatencyTrace.elapsedMs(llmStart));
                            latencyTrace.setOutputTokens(estimateTokens(fullAnswer.toString()));
                            TokenUsageDto usage = RagTokenAudit.finish(true);
                            tokenUsageFinished.set(true);
                            long sourceStart = RagLatencyTrace.now();
                            List<ChatResponse.SourceDto> responseSources =
                                    applyAnswerAwareSourceCap(fullAnswer.toString(), sources, request);
                            latencyTrace.addSourceMs(RagLatencyTrace.elapsedMs(sourceStart));
                            emitter.send(
                                    SseEmitter.event()
                                            .name("done")
                                            .data(buildStreamDonePayload(responseSources, usage, request),
                                                    playgroundDoneMediaType(request))
                            );
                            emitter.complete();
                            saveChatMessage(session, MessageRole.ASSISTANT, fullAnswer.toString(), responseSources);
                            closeLatency.run();
                        } catch (IOException e) {
                            log.error("[Stream] Lỗi khi hoàn tất SSE: {}", e.getMessage(), e);
                            emitter.completeWithError(e);
                            closeLatency.run();
                        }
                    }

                    @Override
                    public void onError(Throwable error) {
                        if (!llmFallbackService.isRateLimitException(error)) {
                            log.error("[Stream] LLM error (không phải rate limit): {}", error.getMessage(), error);
                            if (emitterDone.compareAndSet(false, true)) {
                                emitter.completeWithError(error);
                                closeLatency.run();
                            }
                            return;
                        }

                        // Rate limit → fallback sang model khác (non-streaming)
                        log.warn("[Stream] Model chính bị rate limit. Chuyển sang fallback non-streaming...");
                        try {
                            String fallbackAnswer = llmFallbackService.generateFallbackAnswer(
                                    messages, streamLlmOptions.effective());
                            latencyTrace.addLlmTotalMs(RagLatencyTrace.elapsedMs(llmStart));
                            latencyTrace.setOutputTokens(estimateTokens(fallbackAnswer));
                            TokenUsageDto usage = RagTokenAudit.finish(!isOverloadAnswer(fallbackAnswer));
                            tokenUsageFinished.set(true);
                            fullAnswer.setLength(0);
                            fullAnswer.append(fallbackAnswer);

                            if (emitterDone.compareAndSet(false, true)) {
                                // Phát từng từ để giữ trải nghiệm streaming
                                for (String word : fallbackAnswer.split("(?<=\\s)")) {
                                    emitter.send(
                                            SseEmitter.event()
                                                    .name("token")
                                                    .data("{\"token\":\"" + escapeJson(word) + "\"}", MediaType.APPLICATION_JSON)
                                    );
                                }
                                long sourceStart = RagLatencyTrace.now();
                                List<ChatResponse.SourceDto> responseSources =
                                        applyAnswerAwareSourceCap(fallbackAnswer, sources, request);
                                latencyTrace.addSourceMs(RagLatencyTrace.elapsedMs(sourceStart));
                                emitter.send(
                                        SseEmitter.event()
                                                .name("done")
                                                .data(buildStreamDonePayload(responseSources, usage, request),
                                                        playgroundDoneMediaType(request))
                                );
                                emitter.complete();
                                saveChatMessage(session, MessageRole.ASSISTANT, fallbackAnswer, responseSources);
                                closeLatency.run();
                            }
                        } catch (Exception fallbackError) {
                            log.error("[Stream] Fallback cũng thất bại: {}", fallbackError.getMessage(), fallbackError);
                            if (emitterDone.compareAndSet(false, true)) {
                                emitter.completeWithError(fallbackError);
                                closeLatency.run();
                            }
                        }
                    }
                });

            } catch (Exception e) {
                log.error("[Stream] Lỗi chatStream: {}", e.getMessage(), e);
                emitter.completeWithError(e);
                closeLatency.run();
            } finally {
                if (!tokenUsageFinished.get() && RagTokenAudit.hasActiveState()) {
                    RagTokenAudit.finish(false);
                }
                if (!asyncLlmStarted.get()) {
                    closeLatency.run();
                }
            }
        });

        return emitter;
    }

    private static RagTokenAudit.Mode resolveAuditMode(ChatRequest request) {
        if (request != null && Boolean.TRUE.equals(request.getPlaygroundDebugSources())) {
            return RagTokenAudit.Mode.PLAYGROUND;
        }
        return RagTokenAudit.Mode.CHAT;
    }

    static boolean isOverloadAnswer(String answer) {
        return answer != null && answer.contains("quá tải");
    }

    private void recordPreLlmMetrics(
            ChatRequest request,
            TopKResolution topKResolution,
            List<RetrievedContext> contexts,
            List<ChatMessage> chatHistory,
            String question,
            String userPrompt,
            String systemPrompt,
            LlmGenerationResolution llmOptions
    ) {
        boolean compareMode = request != null && Boolean.TRUE.equals(request.getPlaygroundDebugSources());
        RagLatencyTrace trace = RagLatencyTrace.current();
        int contextChars = RagTokenAudit.contextCharsFromRetrieved(contexts);
        RagTokenAudit.recordPreLlm(
                groqChatModel,
                llmOptions.effective().temperature(),
                llmOptions.effective().maxTokens(),
                topKResolution.effective(),
                contexts != null ? contexts.size() : 0,
                systemPrompt,
                chatHistory,
                contextChars,
                question,
                userPrompt
        );
        if (trace != null) {
            trace.setContextStats(contexts != null ? contexts.size() : 0, contextChars);
            trace.setEstimatedPromptTokens(estimateTokens(systemPrompt) + estimateTokens(userPrompt));
            trace.setMaxTokens(llmOptions.requestedMaxTokens() != null
                    ? llmOptions.requestedMaxTokens()
                    : llmOptions.configuredMaxTokens() != null
                    ? llmOptions.configuredMaxTokens()
                    : LlmGenerationOptions.DEFAULT_MAX_TOKENS,
                    llmOptions.effective().maxTokens());
        }
        if (compareMode) {
            RagTokenAudit.markCompareMode(true);
        }
    }

    private boolean isPlaygroundDebugRequest(ChatRequest request) {
        return request != null && Boolean.TRUE.equals(request.getPlaygroundDebugSources());
    }

    private MediaType playgroundDoneMediaType(ChatRequest request) {
        return isPlaygroundDebugRequest(request) ? MediaType.APPLICATION_JSON : MediaType.TEXT_PLAIN;
    }

    private String buildStreamDonePayload(
            List<ChatResponse.SourceDto> sources,
            TokenUsageDto tokenUsage,
            ChatRequest request
    ) {
        if (isPlaygroundDebugRequest(request)) {
            return buildStreamDoneData(sources, tokenUsage);
        }
        return buildSourcesJson(sources);
    }

    private String buildStreamDoneData(List<ChatResponse.SourceDto> sources, TokenUsageDto tokenUsage) {
        return "{\"sources\":" + buildSourcesJson(sources) + ",\"tokenUsage\":" + tokenUsageToJson(tokenUsage) + "}";
    }

    private String tokenUsageToJson(TokenUsageDto u) {
        if (u == null) {
            return "null";
        }
        return "{"
                + "\"estimatedInputTokens\":" + jsonInt(u.getEstimatedInputTokens())
                + ",\"reservedOutputTokens\":" + jsonInt(u.getReservedOutputTokens())
                + ",\"estimatedTotalRequestTokens\":" + jsonInt(u.getEstimatedTotalRequestTokens())
                + ",\"actualPromptTokens\":" + jsonNullableInt(u.getActualPromptTokens())
                + ",\"actualCompletionTokens\":" + jsonNullableInt(u.getActualCompletionTokens())
                + ",\"actualTotalTokens\":" + jsonNullableInt(u.getActualTotalTokens())
                + ",\"providerRequestedTokens\":" + jsonNullableInt(u.getProviderRequestedTokens())
                + ",\"systemChars\":" + jsonInt(u.getSystemChars())
                + ",\"historyChars\":" + jsonInt(u.getHistoryChars())
                + ",\"contextChars\":" + jsonInt(u.getContextChars())
                + ",\"questionChars\":" + jsonInt(u.getQuestionChars())
                + ",\"promptChars\":" + jsonInt(u.getPromptChars())
                + ",\"historyMessages\":" + jsonInt(u.getHistoryMessages())
                + ",\"finalContexts\":" + jsonInt(u.getFinalContexts())
                + ",\"contextTopN\":" + jsonInt(u.getContextTopN())
                + ",\"embeddingCalls\":" + jsonInt(u.getEmbeddingCalls())
                + ",\"rerankCalls\":" + jsonInt(u.getRerankCalls())
                + ",\"llmCallIndex\":" + jsonInt(u.getLlmCallIndex())
                + ",\"model\":\"" + escapeJson(u.getModel()) + "\""
                + ",\"provider\":\"" + escapeJson(u.getProvider()) + "\""
                + ",\"requestId\":\"" + escapeJson(u.getRequestId()) + "\""
                + ",\"compareMode\":" + (Boolean.TRUE.equals(u.getCompareMode()) ? "true" : "false")
                + ",\"success\":" + (Boolean.TRUE.equals(u.getSuccess()) ? "true" : "false")
                + ",\"errorType\":" + jsonNullableString(u.getErrorType())
                + ",\"errorCode\":" + jsonNullableString(u.getErrorCode())
                + "}";
    }

    private static String jsonInt(Integer value) {
        return value == null ? "0" : String.valueOf(value);
    }

    private static String jsonNullableInt(Integer value) {
        return value == null ? "null" : String.valueOf(value);
    }

    private static String jsonNullableString(String value) {
        return value == null ? "null" : "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    /**
     * Precedence: request topK → {@code uiConfig.modelConfig.topK} → query-type default in retrieval.
     */
    static TopKResolution resolveTopK(Integer requestTopK, Integer configuredTopK) {
        if (requestTopK != null) {
            return new TopKResolution(TopKSource.REQUEST, requestTopK, configuredTopK, requestTopK);
        }
        if (configuredTopK != null) {
            return new TopKResolution(TopKSource.MODEL_CONFIG, null, configuredTopK, configuredTopK);
        }
        return new TopKResolution(TopKSource.DEFAULT, null, configuredTopK, null);
    }

    private TopKResolution resolveRetrievalTopK(UUID widgetId, Integer requestTopK) {
        Integer configuredTopK = null;
        if (requestTopK == null) {
            configuredTopK = loadUiConfig(widgetId)
                    .map(WidgetService::parseModelConfigTopK)
                    .orElse(null);
        }
        TopKResolution resolution = resolveTopK(requestTopK, configuredTopK);
        log.info("[RAG][topN] finalContextTopN source={} requested={} configured={} effective={}",
                resolution.source(), resolution.requested(), resolution.configured(), resolution.effective());
        return resolution;
    }

    static ConfigParamSource resolveParamSource(Object requestValue, Object configuredValue) {
        if (requestValue != null) {
            return ConfigParamSource.REQUEST;
        }
        if (configuredValue != null) {
            return ConfigParamSource.MODEL_CONFIG;
        }
        return ConfigParamSource.DEFAULT;
    }

    static LlmGenerationResolution resolveLlmGeneration(
            Double requestTemperature,
            Integer requestMaxTokens,
            Double configuredTemperature,
            Integer configuredMaxTokens
    ) {
        Double tempCandidate = requestTemperature != null ? requestTemperature : configuredTemperature;
        Integer maxTokensCandidate = requestMaxTokens != null ? requestMaxTokens : configuredMaxTokens;
        LlmGenerationOptions effective = LlmGenerationOptions.normalized(tempCandidate, maxTokensCandidate);
        return new LlmGenerationResolution(
                resolveParamSource(requestTemperature, configuredTemperature),
                resolveParamSource(requestMaxTokens, configuredMaxTokens),
                requestTemperature,
                configuredTemperature,
                requestMaxTokens,
                configuredMaxTokens,
                effective
        );
    }

    private LlmGenerationResolution resolveLlmGenerationOptions(UUID widgetId, ChatRequest request) {
        return resolveLlmGenerationOptions(widgetId, request, request != null ? request.getMessage() : "", null);
    }

    private LlmGenerationResolution resolveLlmGenerationOptions(
            UUID widgetId,
            ChatRequest request,
            String question,
            QueryAnalyzerService.QueryType queryType
    ) {
        Double requestTemperature = request.getTemperature();
        Integer requestMaxTokens = request.getMaxTokens();

        Double configuredTemperature = null;
        Integer configuredMaxTokens = null;
        if (requestTemperature == null || requestMaxTokens == null) {
            var uiConfig = loadUiConfig(widgetId).orElse(null);
            if (requestTemperature == null) {
                configuredTemperature = WidgetService.parseModelConfigTemperature(uiConfig);
            }
            if (requestMaxTokens == null) {
                configuredMaxTokens = WidgetService.parseModelConfigMaxTokens(uiConfig);
            }
        }

        LlmGenerationResolution baseResolution = resolveLlmGeneration(
                requestTemperature,
                requestMaxTokens,
                configuredTemperature,
                configuredMaxTokens
        );
        int upperBound = baseResolution.effective().maxTokens();
        int adaptiveMaxTokens = resolveAdaptiveMaxTokens(question, queryType, upperBound);
        LlmGenerationResolution resolution = new LlmGenerationResolution(
                baseResolution.temperatureSource(),
                baseResolution.maxTokensSource(),
                baseResolution.requestedTemperature(),
                baseResolution.configuredTemperature(),
                baseResolution.requestedMaxTokens(),
                baseResolution.configuredMaxTokens(),
                new LlmGenerationOptions(baseResolution.effective().temperature(), adaptiveMaxTokens)
        );
        log.info(
                "[LLM] generation options tempSource={} maxTokensSource={} "
                        + "requestedTemp={} configuredTemp={} requestedMaxTokens={} configuredMaxTokens={} "
                        + "effectiveTemperature={} effectiveMaxTokens={} adaptiveReason={}",
                resolution.temperatureSource(),
                resolution.maxTokensSource(),
                resolution.requestedTemperature(),
                resolution.configuredTemperature(),
                resolution.requestedMaxTokens(),
                resolution.configuredMaxTokens(),
                resolution.effective().temperature(),
                resolution.effective().maxTokens(),
                adaptiveMaxTokensReason(question, queryType)
        );
        return resolution;
    }

    static int resolveAdaptiveMaxTokens(String question, QueryAnalyzerService.QueryType queryType, int upperBound) {
        int cap = switch (adaptiveMaxTokensReason(question, queryType)) {
            case "list_like" -> 768;
            case "multi_attribute_lookup" -> 512;
            case "compare_like" -> 512;
            case "fact_like" -> 384;
            default -> 512;
        };
        return Math.max(LlmGenerationOptions.MIN_MAX_TOKENS, Math.min(upperBound, cap));
    }

    static QueryAnalyzerService.QueryType queryTypeFromRetrievalResult(RagRetrievalService.RetrievalResult result) {
        QueryAnalysisResult analysis = result != null ? result.analysis() : null;
        return analysis != null && analysis.queryType() != null
                ? analysis.queryType()
                : QueryAnalyzerService.QueryType.NORMAL_FACT;
    }

    static String adaptiveMaxTokensReason(String question, QueryAnalyzerService.QueryType queryType) {
        String normalized = QuerySignalExtractor.normalize(question == null ? "" : question);
        QuerySignalExtractor.QuerySignals signals = QuerySignalExtractor.extract(question == null ? "" : question);
        if (queryType == QueryAnalyzerService.QueryType.LIST_ALL
                || queryType == QueryAnalyzerService.QueryType.COUNT_QUERY
                || normalized.contains("liet ke")
                || normalized.contains("tat ca")
                || normalized.contains("danh sach")) {
            return "list_like";
        }
        if (isMultiAttributeLookup(normalized, signals, queryType)) {
            return "multi_attribute_lookup";
        }
        if (normalized.contains("so sanh")
                || normalized.contains("compare")
                || (CellAwareTableRowScorer.isCompareQuery(question, signals)
                && (normalized.contains(" va ")
                || normalized.contains(" voi ")
                || normalized.contains(" and ")
                || normalized.contains(" vs ")))) {
            return "compare_like";
        }
        if (queryType == QueryAnalyzerService.QueryType.NORMAL_FACT
                || queryType == QueryAnalyzerService.QueryType.TABLE_LOOKUP
                || !signals.identifiers().isEmpty()
                || !signals.dates().isEmpty()
                || !signals.structuredLabels().isEmpty()
                || normalized.matches(".*\\b(ai|gi|nao|o dau|khi nao|may|bao nhieu)\\b.*")) {
            return "fact_like";
        }
        return "default";
    }

    private static boolean isMultiAttributeLookup(
            String normalized,
            QuerySignalExtractor.QuerySignals signals,
            QueryAnalyzerService.QueryType queryType
    ) {
        boolean rowLike = queryType == QueryAnalyzerService.QueryType.TABLE_LOOKUP
                || queryType == QueryAnalyzerService.QueryType.NORMAL_FACT
                || (signals != null && (!signals.identifiers().isEmpty()
                || !signals.structuredLabels().isEmpty()
                || !signals.numbers().isEmpty()));
        if (!rowLike || normalized == null || normalized.isBlank()) {
            return false;
        }
        long separators = normalized.chars().filter(ch -> ch == ',' || ch == ';').count();
        int questionMarkers = 0;
        for (String marker : List.of(" nao", " may", " gi", " dau", " khi nao", " which", " what", " where", " when")) {
            if (normalized.contains(marker)) {
                questionMarkers++;
            }
        }
        return separators >= 2
                || questionMarkers >= 3
                || (queryType == QueryAnalyzerService.QueryType.TABLE_LOOKUP
                && signals != null
                && signals.structuredLabels().size() >= 2);
    }

    static int estimateTokens(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return Math.max(1, (int) Math.ceil(text.length() / 4.0));
    }

    private java.util.Optional<Map<String, Object>> loadUiConfig(UUID widgetId) {
        return widgetConfigRepository.findById(widgetId).map(WidgetConfig::getUiConfig);
    }

    private ChatSession getOrCreateSession(String sessionKeyStr, UUID widgetId) {
        UUID sessionKey;

        if (sessionKeyStr == null || sessionKeyStr.isBlank()) {
            sessionKey = UUID.randomUUID();
        } else {
            sessionKey = UUID.fromString(sessionKeyStr);
        }

        return chatSessionRepository.findBySessionKeyAndWidgetConfigId(sessionKey, widgetId)
                .orElseGet(() -> {
                    WidgetConfig widget = widgetConfigRepository.findById(widgetId)
                            .orElseThrow(() -> new RuntimeException("Không tìm thấy Widget ID: " + widgetId));

                    ChatSession newSession = ChatSession.builder()
                            .sessionKey(sessionKey)
                            .widgetConfig(widget)
                            .widgetOrigin("web-client")
                            .title("Chat Session")
                            .build();

                    return chatSessionRepository.save(newSession);
                });
    }

    static int resolveSourcePresentationCap(ChatRequest request, TopKResolution topKResolution) {
        if (Boolean.TRUE.equals(request.getPlaygroundDebugSources()) && topKResolution != null) {
            return topKResolution.effective();
        }
        return MAX_RESPONSE_SOURCES;
    }

    /**
     * Maps retrieved contexts to API sources with dedupe + cap only (no answer-aware trim).
     */
    List<ChatResponse.SourceDto> buildSourceDtosForResponse(List<RetrievedContext> contexts) {
        return buildSourceDtosForResponse(contexts, MAX_RESPONSE_SOURCES);
    }

    List<ChatResponse.SourceDto> buildSourceDtosForResponse(List<RetrievedContext> contexts, int maxSources) {
        if (contexts == null || contexts.isEmpty()) {
            return List.of();
        }

        int cap = Math.max(1, maxSources);
        List<RetrievedContext> deduped = dedupeContextsForPresentation(contexts);
        int limit = Math.min(deduped.size(), cap);
        List<RetrievedContext> limited = deduped.subList(0, limit);

        if (limited.size() < contexts.size()) {
            log.info("[Chat] Response sources capped: {} retrieved → {} deduped → {} returned (cap={})",
                    contexts.size(), deduped.size(), limited.size(), cap);
        }

        return limited.stream().map(this::toSourceDto).toList();
    }

    List<ChatResponse.SourceDto> applyAnswerAwareSourceCap(
            String answer,
            List<ChatResponse.SourceDto> sources,
            ChatRequest request
    ) {
        if (sources == null || sources.isEmpty()) {
            return List.of();
        }
        if (request != null && Boolean.TRUE.equals(request.getPlaygroundDebugSources())) {
            return sources;
        }
        if (!isLeadingRefusalAnswer(answer)) {
            return sources;
        }
        if (sources.size() <= MAX_REFUSAL_RESPONSE_SOURCES) {
            return sources;
        }
        log.info("[Chat] Leading refusal-like answer (OOS/pivot) → response sources {} → {}",
                sources.size(), MAX_REFUSAL_RESPONSE_SOURCES);
        return List.copyOf(sources.subList(0, MAX_REFUSAL_RESPONSE_SOURCES));
    }

    static boolean isRefusalLikeAnswer(String answer) {
        if (answer == null || answer.isBlank()) {
            return false;
        }
        String normalized = answer.toLowerCase(Locale.ROOT);
        for (String marker : REFUSAL_ANSWER_MARKERS) {
            if (normalized.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Refusal cap (≤2 sources) applies only when the answer is refusal-like and lacks substantive facts
     * (e.g. policy codes, numbered list). Partial answers that mention missing items but list real codes
     * are not treated as pure refusal.
     *
     * <p>Note: production source-cap path uses {@link #isLeadingRefusalAnswer} which additionally
     * handles OOS pivot answers. This method is retained for unit-test coverage.
     */
    static boolean isPureRefusalLikeAnswer(String answer) {
        if (!isRefusalLikeAnswer(answer)) {
            return false;
        }
        return !hasSubstantiveFactualContent(answer);
    }

    /**
     * Returns {@code true} when the answer opens with an OOS/refusal statement before any factual
     * content. This covers both pure OOS refusals and "pivot" answers where the LLM refuses the
     * actual question then pivots to listing unrelated document content.
     *
     * <p>Rule: if the first occurrence of a refusal marker appears <em>before</em> the first
     * policy code ({@code WORD-123}) or numbered list item, the answer is treated as a leading
     * refusal and the OOS source cap (≤{@value MAX_REFUSAL_RESPONSE_SOURCES}) is applied.
     *
     * <p>Partial in-scope answers ("ALPHA-111 found. Không tìm thấy Eta.") are NOT matched because
     * the factual code precedes the refusal phrase.
     *
     * <p>Playground debug path bypasses this check entirely via {@code playgroundDebugSources}.
     */
    static boolean isLeadingRefusalAnswer(String answer) {
        if (!isRefusalLikeAnswer(answer)) {
            return false;
        }
        String normalized = answer.toLowerCase(Locale.ROOT);
        int refusalPos = Integer.MAX_VALUE;
        for (String marker : REFUSAL_ANSWER_MARKERS) {
            int pos = normalized.indexOf(marker);
            if (pos >= 0) {
                refusalPos = Math.min(refusalPos, pos);
            }
        }
        // Any policy code appearing BEFORE the first refusal marker means factual content
        // leads → this is a partial in-scope answer, not an OOS/pivot answer.
        Matcher codeMatcher = FACTUAL_POLICY_CODE.matcher(answer);
        while (codeMatcher.find()) {
            if (codeMatcher.start() < refusalPos) {
                return false;
            }
        }
        // Same logic for numbered list items (e.g. "1. Alpha: …").
        Matcher listMatcher = NUMBERED_LIST_ITEM.matcher(answer);
        while (listMatcher.find()) {
            if (listMatcher.start() < refusalPos) {
                return false;
            }
        }
        return true;
    }

    static boolean hasSubstantiveFactualContent(String answer) {
        if (answer == null || answer.isBlank()) {
            return false;
        }
        if (FACTUAL_POLICY_CODE.matcher(answer).find()) {
            return true;
        }
        if (NUMBERED_LIST_ITEM.matcher(answer).find()) {
            return true;
        }
        long colonLines = answer.lines().filter(line -> line.contains(":")).count();
        return colonLines >= 3;
    }

    static List<RetrievedContext> dedupeContextsForPresentation(List<RetrievedContext> contexts) {
        List<RetrievedContext> result = new ArrayList<>();
        Set<String> seenKeys = new LinkedHashSet<>();

        for (RetrievedContext ctx : contexts) {
            if (ctx == null) {
                continue;
            }
            String key = presentationDedupeKey(ctx);
            if (seenKeys.add(key)) {
                result.add(ctx);
            }
        }
        return result;
    }

    static String presentationDedupeKey(RetrievedContext ctx) {
        if (ctx.getChunkId() != null) {
            return "id:" + ctx.getChunkId();
        }
        String preview = normalizeContentPreview(ctx.getContent());
        return String.join("|",
                nullToEmpty(ctx.getFileName()),
                nullToEmpty(ctx.getSectionTitle()),
                nullToEmpty(ctx.getChunkType()),
                preview);
    }

    static String normalizeContentPreview(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        String collapsed = content.replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
        if (collapsed.length() <= CONTENT_PREVIEW_DEDUP_CHARS) {
            return collapsed;
        }
        return collapsed.substring(0, CONTENT_PREVIEW_DEDUP_CHARS);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private ChatResponse.SourceDto toSourceDto(RetrievedContext ctx) {
        return ChatResponse.SourceDto.builder()
                .fileName(ctx.getFileName())
                .sectionTitle(ctx.getSectionTitle())
                .pages(buildPageRange(ctx.getPageStart(), ctx.getPageEnd()))
                .chunkType(ctx.getChunkType())
                .chunkText(ctx.getContent())
                .build();
    }

    private String buildPageRange(Integer pageStart, Integer pageEnd) {
        if (pageStart == null && pageEnd == null) {
            return "";
        }

        if (pageStart != null && pageEnd != null) {
            if (pageStart.equals(pageEnd)) {
                return String.valueOf(pageStart);
            }
            return pageStart + "-" + pageEnd;
        }

        return String.valueOf(pageStart != null ? pageStart : pageEnd);
    }

    /**
     * Persists a chat message. USER messages are always persisted synchronously so
     * the current-turn history read (which follows immediately) sees them.
     *
     * <p>ASSISTANT messages are persisted asynchronously when
     * {@code rag.runtime.async-persist.enabled=true}, removing their JPA/JSON save
     * time from the user-visible response path. If the async executor queue is full,
     * the rejection handler falls back to caller-thread execution (sync) with a
     * warning log — persistence is never silently dropped.
     */
    private void saveChatMessage(
            ChatSession session,
            MessageRole role,
            String content,
            List<ChatResponse.SourceDto> sources
    ) {
        if (role == MessageRole.ASSISTANT && asyncPersistEnabled && chatPersistExecutor != null) {
            persistAssistantMessageAsync(session, content, sources);
        } else {
            persistChatMessageSync(session, role, content, sources);
        }
    }

    /**
     * Sync persist path — always used for USER messages, and for ASSISTANT when
     * async is disabled or not available.
     */
    private void persistChatMessageSync(
            ChatSession session,
            MessageRole role,
            String content,
            List<ChatResponse.SourceDto> sources
    ) {
        long persistStart = RagLatencyTrace.now();
        try {
            ChatMessage.ChatMessageBuilder builder = ChatMessage.builder()
                    .session(session)
                    .role(role)
                    .content(content);
            if (sources != null && !sources.isEmpty()) {
                builder.sources(toSourceMaps(sources));
            }
            chatMessageRepository.save(builder.build());
        } finally {
            RagLatencyTrace trace = RagLatencyTrace.current();
            if (trace != null) {
                trace.addPersistMs(RagLatencyTrace.elapsedMs(persistStart));
                if (role == MessageRole.ASSISTANT) {
                    trace.setPersistMode("sync");
                }
            }
        }
    }

    /**
     * Submits the assistant message save to the bounded {@code chatPersistExecutor}.
     * Source maps are serialized in the caller thread before submission to avoid
     * cross-thread access to the {@link ChatResponse.SourceDto} objects.
     *
     * <p>The rejection handler in {@link AsyncPersistConfig} will run the task
     * synchronously (CallerRuns-style) and log a warning if the queue is full.
     */
    private void persistAssistantMessageAsync(
            ChatSession session,
            String content,
            List<ChatResponse.SourceDto> sources
    ) {
        UUID sessionId = session.getId();
        List<Map<String, Object>> sourceMaps =
                (sources != null && !sources.isEmpty()) ? toSourceMaps(sources) : null;

        if (logPersistPayloadSize && sourceMaps != null) {
            int approxBytes = sourceMaps.stream()
                    .mapToInt(m -> m.values().stream()
                            .mapToInt(v -> v instanceof String s ? s.length() : 4)
                            .sum())
                    .sum();
            log.debug("[ChatPersistAsync] payloadApproxBytes={} session={}", approxBytes, sessionId);
        }

        RagLatencyTrace trace = RagLatencyTrace.current();
        String traceId = trace != null ? trace.traceId() : "none";
        long submitStart = RagLatencyTrace.now();

        chatPersistExecutor.execute(() -> {
            long taskStart = RagLatencyTrace.now();
            try {
                // getReferenceById avoids a SELECT — proxy is resolved only for FK insert
                ChatSession sessionRef = chatSessionRepository.getReferenceById(sessionId);
                ChatMessage.ChatMessageBuilder builder = ChatMessage.builder()
                        .session(sessionRef)
                        .role(MessageRole.ASSISTANT)
                        .content(content);
                if (sourceMaps != null) {
                    builder.sources(sourceMaps);
                }
                chatMessageRepository.save(builder.build());
                log.info("[ChatPersistAsync] status=OK trace={} session={} elapsedMs={}",
                        traceId, sessionId, RagLatencyTrace.elapsedMs(taskStart));
            } catch (Exception e) {
                log.error("[ChatPersistAsync] status=FAIL trace={} session={} elapsedMs={} error={}",
                        traceId, sessionId, RagLatencyTrace.elapsedMs(taskStart), e.getMessage());
            }
        });

        if (trace != null) {
            trace.addPersistMs(RagLatencyTrace.elapsedMs(submitStart));
            trace.setPersistMode("async");
        }
    }

    private List<Map<String, Object>> toSourceMaps(List<ChatResponse.SourceDto> sources) {
        return sources.stream()
                .map(src -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("fileName", src.getFileName());
                    map.put("sectionTitle", src.getSectionTitle());
                    map.put("pages", src.getPages());
                    map.put("chunkType", src.getChunkType());
                    map.put("chunkText", src.getChunkText());
                    return map;
                })
                .toList();
    }

    private String buildSourcesJson(List<ChatResponse.SourceDto> sources) {
        if (sources == null || sources.isEmpty()) {
            return "[]";
        }

        StringBuilder sb = new StringBuilder("[");

        for (int i = 0; i < sources.size(); i++) {
            ChatResponse.SourceDto src = sources.get(i);

            sb.append("{")
                    .append("\"fileName\":\"").append(escapeJson(src.getFileName())).append("\",")
                    .append("\"sectionTitle\":\"").append(escapeJson(src.getSectionTitle())).append("\",")
                    .append("\"pages\":\"").append(escapeJson(src.getPages())).append("\",")
                    .append("\"chunkType\":\"").append(escapeJson(src.getChunkType())).append("\",")
                    .append("\"chunkText\":\"").append(escapeJson(src.getChunkText())).append("\"");
            sb.append("}");

            if (i < sources.size() - 1) {
                sb.append(",");
            }
        }

        sb.append("]");
        return sb.toString();
    }

    private String escapeJson(String text) {
        if (text == null) {
            return "";
        }

        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
