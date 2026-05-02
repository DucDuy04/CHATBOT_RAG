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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

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
        RagRetrievalService.RetrievalResult retrievalResult =
                ragRetrievalService.retrieveWithMetadata(question, widgetId);
        List<RetrievedContext> contexts = retrievalResult.contexts();
        String lockedScopeLabel = retrievalResult.lockedScopeLabel();

        log.info("[Chat] Retrieval expanded được {} contexts cho widgetId={} lockedScope={}",
                contexts.size(), widgetId, lockedScopeLabel != null ? lockedScopeLabel : "none");

        List<ChatResponse.SourceDto> sources = buildSourceDtos(contexts);

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

            answer = llmFallbackService.generateWithFallback(messages);
        }

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
                RagRetrievalService.RetrievalResult streamResult =
                        ragRetrievalService.retrieveWithMetadata(question, widgetId);
                List<RetrievedContext> contexts = streamResult.contexts();
                String streamLockedScope = streamResult.lockedScopeLabel();

                log.info("[Stream] Retrieval expanded được {} contexts cho widgetId={} lockedScope={}",
                        contexts.size(), widgetId, streamLockedScope != null ? streamLockedScope : "none");

                List<ChatResponse.SourceDto> sources = buildSourceDtos(contexts);

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

                OpenAiStreamingChatModel streamingModel = OpenAiStreamingChatModel.builder()
                        .apiKey(groqApiKey)
                        .baseUrl(groqBaseUrl)
                        .modelName(groqChatModel)
                        .temperature(0.1)
                        .build();

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
                            emitter.send(
                                    SseEmitter.event()
                                            .name("done")
                                            .data(buildSourcesJson(sources), MediaType.TEXT_PLAIN)
                            );
                            emitter.complete();
                            saveChatMessage(session, MessageRole.ASSISTANT, fullAnswer.toString(), sources);
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
                            String fallbackAnswer = llmFallbackService.generateFallbackAnswer(messages);
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
                                emitter.send(
                                        SseEmitter.event()
                                                .name("done")
                                                .data(buildSourcesJson(sources), MediaType.TEXT_PLAIN)
                                );
                                emitter.complete();
                                saveChatMessage(session, MessageRole.ASSISTANT, fallbackAnswer, sources);
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

    private List<ChatResponse.SourceDto> buildSourceDtos(List<RetrievedContext> contexts) {
        if (contexts == null || contexts.isEmpty()) {
            return List.of();
        }

        return contexts.stream()
                .map(ctx -> ChatResponse.SourceDto.builder()
                        .fileName(ctx.getFileName())
                        .sectionTitle(ctx.getSectionTitle())
                        .pages(buildPageRange(ctx.getPageStart(), ctx.getPageEnd()))
                        .chunkType(ctx.getChunkType())
                        .chunkText(ctx.getContent())
                        .build())
                .toList();
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
