package KLTN.RAG_CHATBOT_BE.service;

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
import KLTN.RAG_CHATBOT_BE.service.QueryAnalyzerService;
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
        int effective() {
            return RagRetrievalService.normalizeAnchorTopK(candidate);
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

    @Value("${groq.api-key}")
    private String groqApiKey;

    @Value("${groq.chat-model}")
    private String groqChatModel;

    @Value("${groq.base-url}")
    private String groqBaseUrl;

    public ChatResponse chat(ChatRequest request, UUID widgetId) {
        String question = request.getMessage();

        ChatSession session = getOrCreateSession(request.getSessionId(), widgetId);

        log.info("[Chat] Nhận câu hỏi session={}, widgetId={}: {}", session.getId(), widgetId, question);

        saveChatMessage(session, MessageRole.USER, question, null);

        QueryAnalyzerService.QueryType queryType = queryAnalyzerService.analyze(question, widgetId);
        TopKResolution topKResolution = resolveRetrievalTopK(widgetId, request.getTopK());
        RagRetrievalService.RetrievalResult retrievalResult =
                ragRetrievalService.retrieveWithMetadata(question, widgetId, topKResolution.candidate());
        List<RetrievedContext> contexts = retrievalResult.contexts();
        String lockedScopeLabel = retrievalResult.lockedScopeLabel();

        log.info("[Chat] Retrieval expanded được {} contexts cho widgetId={} lockedScope={}",
                contexts.size(), widgetId, lockedScopeLabel != null ? lockedScopeLabel : "none");

        List<ChatResponse.SourceDto> sources = buildSourceDtosForResponse(contexts);

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
        } else {
            List<ChatMessage> chatHistory =
                    chatMessageRepository.findTop10BySessionIdOrderByCreatedAtAsc(session.getId());

            String userPrompt = promptBuilderService.buildUserPromptFromRetrievedContexts(
                    question,
                    contexts,
                    chatHistory,
                    queryTypeHint,
                    lockedScopeLabel
            );

            String systemPrompt = promptBuilderService.getSystemPrompt();

            List<dev.langchain4j.data.message.ChatMessage> messages = List.of(
                    SystemMessage.from(systemPrompt),
                    UserMessage.from(userPrompt)
            );

            LlmGenerationResolution llmOptions = resolveLlmGenerationOptions(widgetId, request);
            answer = llmFallbackService.generateWithFallback(messages, llmOptions.effective());
        }

        sources = applyAnswerAwareSourceCap(answer, sources);

        saveChatMessage(session, MessageRole.ASSISTANT, answer, sources);

        return ChatResponse.builder()
                .answer(answer)
                .sources(sources)
                .build();
    }

    public SseEmitter chatStream(ChatRequest request, UUID widgetId) {
        SseEmitter emitter = new SseEmitter(180_000L);
        String question = request.getMessage();

        ChatSession session = getOrCreateSession(request.getSessionId(), widgetId);

        streamingExecutor.execute(() -> {
            try {
                log.info("[Stream] Nhận câu hỏi session={}, widgetId={}: {}", session.getId(), widgetId, question);

                saveChatMessage(session, MessageRole.USER, question, null);

                QueryAnalyzerService.QueryType streamQueryType = queryAnalyzerService.analyze(question, widgetId);
                TopKResolution streamTopK = resolveRetrievalTopK(widgetId, request.getTopK());
                RagRetrievalService.RetrievalResult streamResult =
                        ragRetrievalService.retrieveWithMetadata(question, widgetId, streamTopK.candidate());
                List<RetrievedContext> contexts = streamResult.contexts();
                String streamLockedScope = streamResult.lockedScopeLabel();

                log.info("[Stream] Retrieval expanded được {} contexts cho widgetId={} lockedScope={}",
                        contexts.size(), widgetId, streamLockedScope != null ? streamLockedScope : "none");

                List<ChatResponse.SourceDto> sources = buildSourceDtosForResponse(contexts);

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

                    emitter.send(
                            SseEmitter.event()
                                    .name("token")
                                    .data("{\"token\":\"" + escapeJson(noContext) + "\"}", MediaType.APPLICATION_JSON)
                    );

                    emitter.send(
                            SseEmitter.event()
                                    .name("done")
                                    .data("[]", MediaType.TEXT_PLAIN)
                    );

                    emitter.complete();

                    saveChatMessage(session, MessageRole.ASSISTANT, noContext, null);
                    return;
                }

                List<ChatMessage> chatHistory =
                        chatMessageRepository.findTop10BySessionIdOrderByCreatedAtAsc(session.getId());

                String userPrompt = promptBuilderService.buildUserPromptFromRetrievedContexts(
                        question,
                        contexts,
                        chatHistory,
                        streamQueryTypeHint,
                        streamLockedScope
                );

                String systemPrompt = promptBuilderService.getSystemPrompt();

                List<dev.langchain4j.data.message.ChatMessage> messages = new ArrayList<>();
                messages.add(SystemMessage.from(systemPrompt));
                messages.add(UserMessage.from(userPrompt));

                StringBuilder fullAnswer = new StringBuilder();
                // Guard: đảm bảo emitter chỉ được complete 1 lần khi fallback xảy ra
                AtomicBoolean emitterDone = new AtomicBoolean(false);

                LlmGenerationResolution streamLlmOptions = resolveLlmGenerationOptions(widgetId, request);
                OpenAiStreamingChatModel streamingModel =
                        llmFallbackService.buildStreamingModel(groqChatModel, streamLlmOptions.effective());

                streamingModel.generate(messages, new StreamingResponseHandler<AiMessage>() {
                    @Override
                    public void onNext(String token) {
                        try {
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
                            List<ChatResponse.SourceDto> responseSources =
                                    applyAnswerAwareSourceCap(fullAnswer.toString(), sources);
                            emitter.send(
                                    SseEmitter.event()
                                            .name("done")
                                            .data(buildSourcesJson(responseSources), MediaType.TEXT_PLAIN)
                            );
                            emitter.complete();
                            saveChatMessage(session, MessageRole.ASSISTANT, fullAnswer.toString(), responseSources);
                        } catch (IOException e) {
                            log.error("[Stream] Lỗi khi hoàn tất SSE: {}", e.getMessage(), e);
                            emitter.completeWithError(e);
                        }
                    }

                    @Override
                    public void onError(Throwable error) {
                        if (!llmFallbackService.isRateLimitException(error)) {
                            log.error("[Stream] LLM error (không phải rate limit): {}", error.getMessage(), error);
                            if (emitterDone.compareAndSet(false, true)) {
                                emitter.completeWithError(error);
                            }
                            return;
                        }

                        // Rate limit → fallback sang model khác (non-streaming)
                        log.warn("[Stream] Model chính bị rate limit. Chuyển sang fallback non-streaming...");
                        try {
                            String fallbackAnswer = llmFallbackService.generateFallbackAnswer(
                                    messages, streamLlmOptions.effective());
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
                                List<ChatResponse.SourceDto> responseSources =
                                        applyAnswerAwareSourceCap(fallbackAnswer, sources);
                                emitter.send(
                                        SseEmitter.event()
                                                .name("done")
                                                .data(buildSourcesJson(responseSources), MediaType.TEXT_PLAIN)
                                );
                                emitter.complete();
                                saveChatMessage(session, MessageRole.ASSISTANT, fallbackAnswer, responseSources);
                            }
                        } catch (Exception fallbackError) {
                            log.error("[Stream] Fallback cũng thất bại: {}", fallbackError.getMessage(), fallbackError);
                            if (emitterDone.compareAndSet(false, true)) {
                                emitter.completeWithError(fallbackError);
                            }
                        }
                    }
                });

            } catch (Exception e) {
                log.error("[Stream] Lỗi chatStream: {}", e.getMessage(), e);
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    /**
     * Precedence: request topK → {@code uiConfig.modelConfig.topK} → backend default (via normalize).
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
        log.info("[RAG] retrieval topK source={} requested={} configured={} effective={}",
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

        LlmGenerationResolution resolution = resolveLlmGeneration(
                requestTemperature,
                requestMaxTokens,
                configuredTemperature,
                configuredMaxTokens
        );
        log.info(
                "[LLM] generation options tempSource={} maxTokensSource={} "
                        + "requestedTemp={} configuredTemp={} requestedMaxTokens={} configuredMaxTokens={} "
                        + "effectiveTemperature={} effectiveMaxTokens={}",
                resolution.temperatureSource(),
                resolution.maxTokensSource(),
                resolution.requestedTemperature(),
                resolution.configuredTemperature(),
                resolution.requestedMaxTokens(),
                resolution.configuredMaxTokens(),
                resolution.effective().temperature(),
                resolution.effective().maxTokens()
        );
        return resolution;
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

    /**
     * Maps retrieved contexts to API sources with dedupe + cap only (no answer-aware trim).
     */
    List<ChatResponse.SourceDto> buildSourceDtosForResponse(List<RetrievedContext> contexts) {
        if (contexts == null || contexts.isEmpty()) {
            return List.of();
        }

        List<RetrievedContext> deduped = dedupeContextsForPresentation(contexts);
        int limit = Math.min(deduped.size(), MAX_RESPONSE_SOURCES);
        List<RetrievedContext> limited = deduped.subList(0, limit);

        if (limited.size() < contexts.size()) {
            log.info("[Chat] Response sources capped: {} retrieved → {} deduped → {} returned",
                    contexts.size(), deduped.size(), limited.size());
        }

        return limited.stream().map(this::toSourceDto).toList();
    }

    List<ChatResponse.SourceDto> applyAnswerAwareSourceCap(
            String answer,
            List<ChatResponse.SourceDto> sources
    ) {
        if (sources == null || sources.isEmpty()) {
            return List.of();
        }
        if (!isRefusalLikeAnswer(answer)) {
            return sources;
        }
        if (sources.size() <= MAX_REFUSAL_RESPONSE_SOURCES) {
            return sources;
        }
        log.info("[Chat] Refusal-like answer → response sources {} → {}",
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

    private void saveChatMessage(
            ChatSession session,
            MessageRole role,
            String content,
            List<ChatResponse.SourceDto> sources
    ) {
        ChatMessage.ChatMessageBuilder builder = ChatMessage.builder()
                .session(session)
                .role(role)
                .content(content);

        if (sources != null && !sources.isEmpty()) {
            builder.sources(toSourceMaps(sources));
        }

        chatMessageRepository.save(builder.build());
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
                    .append("\"chunkText\":\"").append(escapeJson(src.getChunkText())).append("\"")
                    .append("}");

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
