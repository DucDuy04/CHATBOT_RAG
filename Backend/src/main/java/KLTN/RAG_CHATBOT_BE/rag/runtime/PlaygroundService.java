package KLTN.RAG_CHATBOT_BE.rag.runtime;

import KLTN.RAG_CHATBOT_BE.domain.chat.ChatMessage;
import KLTN.RAG_CHATBOT_BE.domain.chat.ChatMessageRepository;
import KLTN.RAG_CHATBOT_BE.domain.chat.ChatSession;
import KLTN.RAG_CHATBOT_BE.domain.chat.ChatSessionRepository;
import KLTN.RAG_CHATBOT_BE.domain.widget.WidgetConfigRepository;
import KLTN.RAG_CHATBOT_BE.dto.ChatResponse;
import KLTN.RAG_CHATBOT_BE.dto.PlaygroundCompareResponse;
import KLTN.RAG_CHATBOT_BE.dto.PlaygroundCompareResult;
import KLTN.RAG_CHATBOT_BE.dto.PlaygroundExportResponse;
import KLTN.RAG_CHATBOT_BE.dto.TokenUsageDto;
import KLTN.RAG_CHATBOT_BE.dto.PlaygroundMessageResponse;
import KLTN.RAG_CHATBOT_BE.dto.PlaygroundSessionResponse;
import KLTN.RAG_CHATBOT_BE.dto.RetrievedContext;
import KLTN.RAG_CHATBOT_BE.audit.metrics.RagTokenAudit;
import KLTN.RAG_CHATBOT_BE.rag.analysis.QueryAnalyzerService;
import KLTN.RAG_CHATBOT_BE.rag.prompt.PromptBuilderService;
import KLTN.RAG_CHATBOT_BE.rag.retrieve.RagRetrievalService;
import KLTN.RAG_CHATBOT_BE.llm.LlmFallbackService;
import KLTN.RAG_CHATBOT_BE.llm.LlmGenerationOptions;
import KLTN.RAG_CHATBOT_BE.service.WidgetService;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlaygroundService {

    @Value("${groq.chat-model}")
    private String groqChatModel;

    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final QueryAnalyzerService queryAnalyzerService;
    private final RagRetrievalService ragRetrievalService;
    private final PromptBuilderService promptBuilderService;
    private final LlmFallbackService llmFallbackService;
    private final WidgetConfigRepository widgetConfigRepository;

    public List<PlaygroundSessionResponse> listSessions(UUID chatbotId) {
        List<ChatSession> sessions = chatSessionRepository.findByWidgetConfigIdOrderByUpdatedAtDesc(chatbotId);
        List<PlaygroundSessionResponse> result = new ArrayList<>();

        for (ChatSession session : sessions) {
            List<ChatMessage> messages = chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(session.getId());
            String lastMessage = messages.isEmpty() ? null : messages.get(messages.size() - 1).getContent();

            result.add(PlaygroundSessionResponse.builder()
                    .id(session.getSessionKey().toString())
                    .title(session.getTitle())
                    .chatbotId(session.getWidgetConfig().getId().toString())
                    .messageCount((long) messages.size())
                    .lastMessage(lastMessage)
                    .createdAt(session.getCreatedAt())
                    .updatedAt(session.getUpdatedAt())
                    .messages(mapMessages(messages))
                    .build());
        }

        return result;
    }

    public void deleteSession(UUID sessionKey) {
        ChatSession session = chatSessionRepository.findBySessionKey(sessionKey)
                .orElseThrow(() -> new IllegalArgumentException("Session not found"));

        LocalDateTime now = LocalDateTime.now();
        session.setDeletedAt(now);
        chatSessionRepository.save(session);

        List<ChatMessage> messages = chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(session.getId());
        for (ChatMessage message : messages) {
            message.setDeletedAt(now);
        }
        chatMessageRepository.saveAll(messages);
    }

    public PlaygroundExportResponse exportSession(UUID sessionKey) {
        ChatSession session = chatSessionRepository.findBySessionKey(sessionKey)
                .orElseThrow(() -> new IllegalArgumentException("Session not found"));
        List<ChatMessage> messages = chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(session.getId());

        return PlaygroundExportResponse.builder()
                .sessionId(session.getSessionKey().toString())
                .chatbotId(session.getWidgetConfig().getId().toString())
                .createdAt(session.getCreatedAt())
                .updatedAt(session.getUpdatedAt())
                .messages(mapMessages(messages))
                .build();
    }

    public PlaygroundCompareResponse compare(UUID chatbotId,
                                             String message,
                                             Map<String, Object> configA,
                                             Map<String, Object> configB) {
        return PlaygroundCompareResponse.builder()
                .configA(runCompareOnce(chatbotId, message, safeConfig(configA), RagTokenAudit.Mode.COMPARE_A))
                .configB(runCompareOnce(chatbotId, message, safeConfig(configB), RagTokenAudit.Mode.COMPARE_B))
                .build();
    }

    private PlaygroundCompareResult runCompareOnce(
            UUID chatbotId,
            String question,
            Map<String, Object> config,
            RagTokenAudit.Mode auditMode
    ) {
        long start = System.currentTimeMillis();
        RagTokenAudit.begin(auditMode, chatbotId, null);

        try {
        Integer topKOverride = RagRetrievalService.parseTopKOverride(config);
        RagRetrievalService.RetrievalResult retrievalResult =
                ragRetrievalService.retrieveWithMetadata(question, chatbotId, topKOverride);
        QueryAnalyzerService.QueryType queryType = ChatService.queryTypeFromRetrievalResult(retrievalResult);
        List<RetrievedContext> contexts = retrievalResult.contexts();
        List<ChatResponse.SourceDto> sources = buildSourceDtos(contexts);

        String answer;
        TokenUsageDto tokenUsage;
        if (contexts.isEmpty()) {
            answer = "Tôi không tìm thấy thông tin này trong tài liệu.";
            LlmGenerationOptions emptyLlmOptions = resolveCompareLlmOptions(chatbotId, config);
            int contextTopN = RagRetrievalService.normalizeFinalContextTopN(topKOverride);
            RagTokenAudit.recordPreLlm(
                    groqChatModel,
                    emptyLlmOptions.temperature(),
                    emptyLlmOptions.maxTokens(),
                    contextTopN,
                    0,
                    promptBuilderService.getSystemPrompt(),
                    List.of(),
                    0,
                    question,
                    ""
            );
            tokenUsage = RagTokenAudit.finish(true);
        } else {
            String userPrompt = promptBuilderService.buildUserPromptFromRetrievedContexts(
                    question,
                    contexts,
                    List.of(),
                    queryType.name(),
                    retrievalResult.lockedScopeLabel()
            );

            String systemPrompt = promptBuilderService.getSystemPrompt();
            LlmGenerationOptions llmOptions = resolveCompareLlmOptions(chatbotId, config);
            int contextTopN = RagRetrievalService.normalizeFinalContextTopN(topKOverride);
            RagTokenAudit.recordPreLlm(
                    groqChatModel,
                    llmOptions.temperature(),
                    llmOptions.maxTokens(),
                    contextTopN,
                    contexts.size(),
                    systemPrompt,
                    List.of(),
                    RagTokenAudit.contextCharsFromRetrieved(contexts),
                    question,
                    userPrompt
            );
            answer = llmFallbackService.generateWithFallback(List.of(
                    SystemMessage.from(systemPrompt),
                    UserMessage.from(userPrompt)
            ), llmOptions);
            tokenUsage = RagTokenAudit.finish(!ChatService.isOverloadAnswer(answer));
        }

        long elapsed = System.currentTimeMillis() - start;
        return PlaygroundCompareResult.builder()
                .answer(answer)
                .sources(sources)
                .latency(elapsed)
                .config(config)
                .tokenUsage(tokenUsage)
                .build();
        } finally {
            if (RagTokenAudit.hasActiveState()) {
                RagTokenAudit.finish(false);
            }
        }
    }

    private List<PlaygroundMessageResponse> mapMessages(List<ChatMessage> messages) {
        List<PlaygroundMessageResponse> result = new ArrayList<>();
        for (ChatMessage message : messages) {
            result.add(PlaygroundMessageResponse.builder()
                    .id(message.getId().toString())
                    .role(message.getRole().name().toLowerCase())
                    .content(message.getContent())
                    .createdAt(message.getCreatedAt())
                    .sources(safeSources(message.getSources()))
                    .build());
        }
        return result;
    }

    private List<Map<String, Object>> safeSources(List<Map<String, Object>> sources) {
        if (sources == null) {
            return List.of();
        }
        return sources;
    }

    private Map<String, Object> safeConfig(Map<String, Object> config) {
        if (config == null) {
            return Collections.emptyMap();
        }
        return config;
    }

    private LlmGenerationOptions resolveCompareLlmOptions(UUID chatbotId, Map<String, Object> config) {
        Double requestTemperature = LlmGenerationOptions.parseTemperatureOverride(config);
        Integer requestMaxTokens = LlmGenerationOptions.parseMaxTokensOverride(config);

        Double configuredTemperature = null;
        Integer configuredMaxTokens = null;
        if (requestTemperature == null || requestMaxTokens == null) {
            var uiConfig = widgetConfigRepository.findById(chatbotId)
                    .map(w -> w.getUiConfig())
                    .orElse(null);
            if (requestTemperature == null) {
                configuredTemperature = WidgetService.parseModelConfigTemperature(uiConfig);
            }
            if (requestMaxTokens == null) {
                configuredMaxTokens = WidgetService.parseModelConfigMaxTokens(uiConfig);
            }
        }

        return ChatService.resolveLlmGeneration(
                requestTemperature,
                requestMaxTokens,
                configuredTemperature,
                configuredMaxTokens
        ).effective();
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
}
