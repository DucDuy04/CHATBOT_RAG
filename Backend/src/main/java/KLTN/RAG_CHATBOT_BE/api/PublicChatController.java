package KLTN.RAG_CHATBOT_BE.api;

import KLTN.RAG_CHATBOT_BE.dto.ChatRequest;
import KLTN.RAG_CHATBOT_BE.dto.ChatResponse;
import KLTN.RAG_CHATBOT_BE.dto.PublicChatResponse;
import KLTN.RAG_CHATBOT_BE.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PublicChatController {

    private final ChatService chatService;

    @PostMapping("/chat")
    public ResponseEntity<PublicChatResponse> publicChat(
            @RequestAttribute("Widget-Id") UUID widgetId,
            @RequestBody ChatRequest request
    ) {
        if (request.getMessage() == null || request.getMessage().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        // FE public chat có thể gửi sessionId null/blank.
        String sessionId = request.getSessionId();
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = UUID.randomUUID().toString();
            request.setSessionId(sessionId);
        }

        ChatResponse response = chatService.chat(request, widgetId);
        return ResponseEntity.ok(
                PublicChatResponse.builder()
                        .answer(response.getAnswer())
                        .sessionId(sessionId)
                        .sources(response.getSources())
                        .build()
        );
    }
}
