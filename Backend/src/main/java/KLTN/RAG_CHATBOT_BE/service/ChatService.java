package KLTN.RAG_CHATBOT_BE.service;

import KLTN.RAG_CHATBOT_BE.domain.chat.ChatMessage;
import KLTN.RAG_CHATBOT_BE.domain.chat.ChatSession;
import KLTN.RAG_CHATBOT_BE.domain.chat.ChatMessageRepository;
import KLTN.RAG_CHATBOT_BE.domain.widget.WidgetConfig;
import KLTN.RAG_CHATBOT_BE.domain.widget.WidgetConfigRepository;
import KLTN.RAG_CHATBOT_BE.dto.ChatRequest;
import KLTN.RAG_CHATBOT_BE.dto.ChatResponse;
import KLTN.RAG_CHATBOT_BE.domain.enums.MessageRole; // Dùng Enum ngày 1
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
import java.util.List;
import java.util.UUID;



import KLTN.RAG_CHATBOT_BE.domain.chat.ChatSessionRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final EmbeddingService embeddingService;
    private final PromptBuilderService promptBuilderService;
    private final OpenAiChatModel chatModel;
    
    // Inject thêm các Repository của Ngày 1
    private final ChatMessageRepository chatMessageRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final WidgetConfigRepository widgetConfigRepository;

    @Value("${groq.api-key}")
    private String groqApiKey;

    @Value("${groq.chat-model}")
    private String groqChatModel;

    @Value("${groq.base-url}")
    private String groqBaseUrl;

    private static final int TOP_K = 8;

    // --- HÀM CHAT ĐỒNG BỘ ---
    public ChatResponse chat(ChatRequest request, UUID widgetId) {
        String question = request.getMessage();
        
        // 1. Lấy thông tin Session và Widget
        ChatSession session = getOrCreateSession(request.getSessionId(), widgetId);
        WidgetConfig widget = session.getWidgetConfig();

        log.info("Nhan cau hoi tu session={}: {}", session.getId(), question);

        // 2. Lưu câu hỏi của User
        saveChatMessage(session, MessageRole.USER, question, null);

        // 3. TÌM KIẾM CÓ FILTER THEO WIDGET_ID (Cực kỳ quan trọng)
        List<TextSegment> segments = embeddingService.search(question, TOP_K, widgetId);
        log.info("Tim duoc {} chunks lien quan cho widget {}", segments.size(), widgetId);

        List<String> contextChunks = segments.stream().map(TextSegment::text).toList();
        List<ChatResponse.SourceDto> sources = segments.stream()
                .map(seg -> ChatResponse.SourceDto.builder()
                        .fileName(seg.metadata().getString("fileName"))
                        .chunkText(seg.text())
                        .build())
                .toList();

        List<ChatMessage> chatHistory = chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(session.getId());

        // 4. Build Prompt kèm theo SYSTEM PROMPT của Widget
        // String prompt = promptBuilderService.buildPrompt(widget.getSystemPrompt(), question, contextChunks, chatHistory);
        String prompt = promptBuilderService.buildPrompt(question, contextChunks, chatHistory);

        String answer;
        if (segments.isEmpty()) {
            answer = "Tôi không tìm thấy thông tin liên quan đến câu hỏi của bạn trong tài liệu đã cung cấp.";
        } else {
            answer = chatModel.generate(prompt);
        }

        // 5. Lưu câu trả lời của AI
        saveChatMessage(session, MessageRole.ASSISTANT, answer, buildSourcesJson(sources));

        return ChatResponse.builder().answer(answer).sources(sources).build();
    }

    // --- HÀM CHAT STREAMING (SSE) ---
    public SseEmitter chatStream(ChatRequest request, UUID widgetId) {
        SseEmitter emitter = new SseEmitter(180_000L);
        String question = request.getMessage();

        // Xử lý Session ở Thread chính để tránh lỗi Hibernate Lazy Loading
        ChatSession session = getOrCreateSession(request.getSessionId(), widgetId);
        WidgetConfig widget = session.getWidgetConfig();

        new Thread(() -> {
            try {
                saveChatMessage(session, MessageRole.USER, question, null);

                // LỌC THEO WIDGET ID TRONG QDRANT
                List<TextSegment> segments = embeddingService.search(question, TOP_K, widgetId);
                
                List<String> contextChunks = segments.stream().map(TextSegment::text).toList();
                List<ChatResponse.SourceDto> sources = segments.stream()
                        .map(seg -> ChatResponse.SourceDto.builder()
                                .fileName(seg.metadata().getString("fileName"))
                                .chunkText(seg.text())
                                .build())
                        .toList();

                List<ChatMessage> chatHistory = chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(session.getId());

                if (segments.isEmpty()) {
                    String noContext = "Tôi không tìm thấy thông tin liên quan đến câu hỏi của bạn.";
                    emitter.send(SseEmitter.event().name("token").data("{\"token\":\"" + escapeJson(noContext) + "\"}", MediaType.APPLICATION_JSON));
                    emitter.send(SseEmitter.event().name("done").data("[]", MediaType.TEXT_PLAIN));
                    emitter.complete();
                    saveChatMessage(session, MessageRole.ASSISTANT, noContext, null);
                    return;
                }

                // TRUYỀN SYSTEM PROMPT VÀO
                // String prompt = promptBuilderService.buildPrompt(widget.getSystemPrompt(), question, contextChunks, chatHistory);
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
                            String jsonToken = "{\"token\":\"" + escapeJson(token) + "\"}";
                            emitter.send(SseEmitter.event().name("token").data(jsonToken, MediaType.APPLICATION_JSON));
                        } catch (IOException e) {
                            emitter.completeWithError(e);
                        }
                    }

                    @Override
                    public void onComplete(Response<AiMessage> response) {
                        try {
                            String sourcesJson = buildSourcesJson(sources);
                            emitter.send(SseEmitter.event().name("done").data(sourcesJson, MediaType.TEXT_PLAIN));
                            emitter.complete();
                            
                            // Lưu lại Database khi stream hoàn tất (Đúng chuẩn Ngày 5)
                            saveChatMessage(session, MessageRole.ASSISTANT, fullAnswer.toString(), sourcesJson);
                        } catch (IOException e) {
                            emitter.completeWithError(e);
                        }
                    }

                    @Override
                    public void onError(Throwable error) {
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

    // --- CÁC HÀM BỔ TRỢ ---

    // Quản lý Session: Trình duyệt gửi sessionKey (Dạng UUID string). Tìm nếu có, chưa có thì tạo mới
    private ChatSession getOrCreateSession(String sessionKeyStr, UUID widgetId) {
        UUID sessionKey = UUID.fromString(sessionKeyStr);
        return chatSessionRepository.findBySessionKey(sessionKey)
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

    private void saveChatMessage(ChatSession session, MessageRole role, String content,String sourcesJson) {
        ChatMessage message = ChatMessage.builder()
                .session(session) // Dùng Entity ChatSession
                .role(role)
                .content(content)
                // Lưu ý: Nếu Entity ChatMessage của bạn lưu source dạng String (JSON) thì truyền String, 
                // Nếu lưu dạng List<Map> thì cần dùng ObjectMapper parse chuỗi JSON này ra List.
                // .sources(sourcesJson) 
                .build();
        chatMessageRepository.save(message);
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

}