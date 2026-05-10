package KLTN.RAG_CHATBOT_BE.api;

import KLTN.RAG_CHATBOT_BE.dto.ChatRequest;
import KLTN.RAG_CHATBOT_BE.dto.ChatFeedbackRequest;
import KLTN.RAG_CHATBOT_BE.dto.ChatFeedbackResponse;
import KLTN.RAG_CHATBOT_BE.dto.ChatResponse;
import KLTN.RAG_CHATBOT_BE.service.ChatFeedbackService;
import KLTN.RAG_CHATBOT_BE.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final ChatFeedbackService chatFeedbackService;

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
        @RequestAttribute("Widget-Id") UUID widgetId,
        @RequestBody ChatRequest request) {

        if (request.getMessage() == null || request.getMessage().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "message must not be blank");
        }
        if (request.getSessionId() == null || request.getSessionId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sessionId must not be blank");
        }

        return chatService.chatStream(request, widgetId);
    }

    @PostMapping("/feedback")
    public ResponseEntity<?> submitFeedback(@RequestBody ChatFeedbackRequest request) {
        if (request.getMessageId() == null || request.getMessageId().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "messageId is required"));
        }
        if (request.getRating() == null || (request.getRating() != 1 && request.getRating() != -1)) {
            return ResponseEntity.badRequest().body(Map.of("message", "rating must be 1 or -1"));
        }

        final UUID messageId;
        try {
            messageId = UUID.fromString(request.getMessageId().trim());
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", "messageId must be a UUID"));
        }

        try {
            ChatFeedbackResponse response = chatFeedbackService.submitFeedback(
                    messageId,
                    request.getRating(),
                    request.getComment()
            );
            return ResponseEntity.ok(response);
        } catch (NoSuchElementException ex) {
            return ResponseEntity.status(404).body(Map.of("message", "Message not found"));
        } catch (IllegalStateException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", "Feedback can only be submitted for assistant messages"));
        }
    }
}