package KLTN.RAG_CHATBOT_BE.service;

import KLTN.RAG_CHATBOT_BE.domain.chat.ChatMessage;
import KLTN.RAG_CHATBOT_BE.domain.chat.ChatMessageRepository;
import KLTN.RAG_CHATBOT_BE.dto.ChatRequest;
import KLTN.RAG_CHATBOT_BE.dto.ChatResponse;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.output.Response;
import org.springframework.http.MediaType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final EmbeddingService embeddingService;
    private final PromptBuilderService promptBuilderService;
    private final OpenAiChatModel chatModel;
    private final ChatMessageRepository chatMessageRepository;

    @Value("${groq.api-key}")
    private String groqApiKey;

    @Value("${groq.chat-model}")
    private String groqChatModel;

    @Value("${groq.base-url}")
    private String groqBaseUrl;

    private static final int TOP_K = 5;

    private static final MediaType TEXT_PLAIN_UTF8 = new MediaType("text", "plain", StandardCharsets.UTF_8);

    public ChatResponse chat(ChatRequest request) {
        String sessionId = request.getSessionId();
        String question = request.getMessage();

        log.info("Nhan cau hoi tu session={}: {}", sessionId, question);

        saveChatMessage(sessionId, ChatMessage.MessageRole.USER, question, null);

        List<TextSegment> segments = embeddingService.search(question, TOP_K);
        log.info("Tim duoc {} chunks lien quan", segments.size());

        List<String> contextChunks = segments.stream()
                .map(TextSegment::text)
                .toList();

        List<ChatResponse.SourceDto> sources = segments.stream()
                .map(seg -> ChatResponse.SourceDto.builder()
                        .fileName(seg.metadata().getString("fileName"))
                        .chunkText(seg.text())
                        .build())
                .toList();

        List<ChatMessage> chatHistory = chatMessageRepository
                .findTop10BySessionIdOrderByCreatedAtAsc(sessionId);

        String prompt = promptBuilderService.buildPrompt(question, contextChunks, chatHistory);

        String answer;
        if (segments.isEmpty()) {
            answer = "Toi khong tim thay thong tin lien quan den cau hoi cua ban trong tai lieu da cung cap.";
        } else {
            answer = chatModel.generate(prompt);
        }

        log.info("Groq tra loi xong cho session={}", sessionId);

        saveChatMessage(sessionId, ChatMessage.MessageRole.ASSISTANT, answer, null);

        return ChatResponse.builder()
                .answer(answer)
                .sources(sources)
                .build();
    }

    public SseEmitter chatStream(ChatRequest request) {
        SseEmitter emitter = new SseEmitter(180_000L);

        String sessionId = request.getSessionId();
        String question = request.getMessage();

        new Thread(() -> {
            try {
                saveChatMessage(sessionId, ChatMessage.MessageRole.USER, question, null);

                List<TextSegment> segments = embeddingService.search(question, TOP_K);
                log.info("[Stream] Tim duoc {} chunks cho session={}", segments.size(), sessionId);

                List<String> contextChunks = segments.stream()
                        .map(TextSegment::text)
                        .toList();

                List<ChatResponse.SourceDto> sources = segments.stream()
                        .map(seg -> ChatResponse.SourceDto.builder()
                                .fileName(seg.metadata().getString("fileName"))
                                .chunkText(seg.text())
                                .build())
                        .toList();

                List<ChatMessage> chatHistory = chatMessageRepository
                        .findTop10BySessionIdOrderByCreatedAtAsc(sessionId);

                if (segments.isEmpty()) {
                    String noContext = "Toi khong tim thay thong tin lien quan den cau hoi cua ban trong tai lieu da cung cap.";
                    emitter.send(SseEmitter.event()
                            .name("token")
                            .data(noContext, MediaType.TEXT_PLAIN));
                    emitter.send(SseEmitter.event()
                            .name("done")
                            .data("[]", MediaType.TEXT_PLAIN));
                    emitter.complete();
                    saveChatMessage(sessionId, ChatMessage.MessageRole.ASSISTANT, noContext, null);
                    return;
                }

                String prompt = promptBuilderService.buildPrompt(question, contextChunks, chatHistory);

                OpenAiStreamingChatModel streamingModel = OpenAiStreamingChatModel.builder()
                        .apiKey(groqApiKey)
                        .baseUrl(groqBaseUrl)
                        .modelName(groqChatModel)
                        .temperature(0.7)
                        .build();

                StringBuilder fullAnswer = new StringBuilder();

                streamingModel.generate(prompt, new StreamingResponseHandler<AiMessage>() {
                    @Override
                    public void onNext(String token) {
                        try {
                            fullAnswer.append(token);

                            // ✅ Wrap token vào JSON để giữ nguyên space
                            String jsonToken = "{\"token\":\"" + escapeJson(token) + "\"}";

                            emitter.send(SseEmitter.event()
                                    .name("token")
                                    .data(jsonToken, MediaType.APPLICATION_JSON));
                        } catch (IOException e) {
                            log.error("[Stream] Loi gui token: {}", e.getMessage());
                            emitter.completeWithError(e);
                        }
                    }

                    @Override
                    public void onComplete(Response<AiMessage> response) {
                        try {
                            String sourcesJson = buildSourcesJson(sources);
                            emitter.send(SseEmitter.event()
                                    .name("done")
                                    .data(sourcesJson, MediaType.TEXT_PLAIN)); // ✅ thêm MediaType
                            emitter.complete();
                            saveChatMessage(sessionId, ChatMessage.MessageRole.ASSISTANT,
                                    fullAnswer.toString(), null);
                            log.info("[Stream] Hoan thanh cho session={}", sessionId);
                        } catch (IOException e) {
                            log.error("[Stream] Loi hoan thanh: {}", e.getMessage());
                            emitter.completeWithError(e);
                        }
                    }

                    @Override
                    public void onError(Throwable error) {
                        log.error("[Stream] Loi LLM: {}", error.getMessage());
                        emitter.completeWithError(error);
                    }
                });

            } catch (Exception e) {
                log.error("[Stream] Loi chatStream: {}", e.getMessage());
                emitter.completeWithError(e);
            }
        }).start();

        return emitter;
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

    private void saveChatMessage(String sessionId,
            ChatMessage.MessageRole role,
            String content,
            String sources) {
        ChatMessage message = ChatMessage.builder()
                .sessionId(sessionId)
                .role(role)
                .content(content)
                .sources(sources)
                .build();
        chatMessageRepository.save(message);
    }
}
