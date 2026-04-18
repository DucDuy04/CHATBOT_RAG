package KLTN.RAG_CHATBOT_BE.api;

import KLTN.RAG_CHATBOT_BE.dto.ChatRequest;
import KLTN.RAG_CHATBOT_BE.dto.ChatResponse;
import KLTN.RAG_CHATBOT_BE.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @PostMapping
    public ResponseEntity<ChatResponse> chat(
        @RequestAttribute("Widget-Id") UUID widgetId, // Lấy từ WidgetAuthFilter
        @RequestBody ChatRequest request) {

        if (request.getMessage() == null || request.getMessage().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        if (request.getSessionId() == null || request.getSessionId().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        ChatResponse response = chatService.chat(request, widgetId);
        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(
        @RequestAttribute("Widget-Id") UUID widgetId, // Thêm dòng này
        @RequestBody ChatRequest request) {
        
        return chatService.chatStream(request, widgetId);
    }
}