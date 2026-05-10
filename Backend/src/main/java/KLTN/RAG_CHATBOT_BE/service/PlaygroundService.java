package KLTN.RAG_CHATBOT_BE.service;

import KLTN.RAG_CHATBOT_BE.domain.chat.ChatMessage;
import KLTN.RAG_CHATBOT_BE.domain.chat.ChatMessageRepository;
import KLTN.RAG_CHATBOT_BE.domain.chat.ChatSession;
import KLTN.RAG_CHATBOT_BE.domain.chat.ChatSessionRepository;
import KLTN.RAG_CHATBOT_BE.dto.ChatResponse;
import KLTN.RAG_CHATBOT_BE.dto.PlaygroundCompareResponse;
import KLTN.RAG_CHATBOT_BE.dto.PlaygroundCompareResult;
import KLTN.RAG_CHATBOT_BE.dto.PlaygroundExportResponse;
import KLTN.RAG_CHATBOT_BE.dto.PlaygroundMessageResponse;
import KLTN.RAG_CHATBOT_BE.dto.PlaygroundSessionResponse;
import KLTN.RAG_CHATBOT_BE.dto.RetrievedContext;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PlaygroundService {

    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final QueryAnalyzerService queryAnalyzerService;
    private final RagRetrievalService ragRetrievalService;
    private final PromptBuilderService promptBuilderService;
    private final LlmFallbackService llmFallbackService;

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
                .configA(runCompareOnce(chatbotId, message, safeConfig(configA)))
                .configB(runCompareOnce(chatbotId, message, safeConfig(configB)))
                .build();
    }

    private PlaygroundCompareResult runCompareOnce(UUID chatbotId, String question, Map<String, Object> config) {
        long start = System.currentTimeMillis();

        QueryAnalyzerService.QueryType queryType = queryAnalyzerService.analyze(question, chatbotId);
        RagRetrievalService.RetrievalResult retrievalResult = ragRetrievalService.retrieveWithMetadata(question, chatbotId);
        List<RetrievedContext> contexts = retrievalResult.contexts();
        List<ChatResponse.SourceDto> sources = buildSourceDtos(contexts);

        String answer;
        if (contexts.isEmpty()) {
            answer = "Tôi không tìm thấy thông tin này trong tài liệu.";
        } else {
            String userPrompt = promptBuilderService.buildUserPromptFromRetrievedContexts(
                    question,
                    contexts,
                    List.of(),
                    queryType.name(),
                    retrievalResult.lockedScopeLabel()
            );

            String systemPrompt = promptBuilderService.getSystemPrompt();
            answer = llmFallbackService.generateWithFallback(List.of(
                    SystemMessage.from(systemPrompt),
                    UserMessage.from(userPrompt)
            ));
        }

        long elapsed = System.currentTimeMillis() - start;
        return PlaygroundCompareResult.builder()
                .answer(answer)
                .sources(sources)
                .latency(elapsed)
                .config(config)
                .build();
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
