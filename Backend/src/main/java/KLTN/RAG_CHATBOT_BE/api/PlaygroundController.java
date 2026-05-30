package KLTN.RAG_CHATBOT_BE.api;

import KLTN.RAG_CHATBOT_BE.dto.ChatRequest;
import KLTN.RAG_CHATBOT_BE.dto.PlaygroundCompareRequest;
import KLTN.RAG_CHATBOT_BE.dto.PlaygroundCompareResponse;
import KLTN.RAG_CHATBOT_BE.dto.PlaygroundChatRequest;
import KLTN.RAG_CHATBOT_BE.dto.PlaygroundExportResponse;
import KLTN.RAG_CHATBOT_BE.dto.PlaygroundSessionResponse;
import KLTN.RAG_CHATBOT_BE.dto.SimpleSuccessResponse;
import KLTN.RAG_CHATBOT_BE.rag.runtime.ChatService;
import KLTN.RAG_CHATBOT_BE.rag.runtime.PlaygroundService;
import KLTN.RAG_CHATBOT_BE.llm.LlmGenerationOptions;
import KLTN.RAG_CHATBOT_BE.rag.retrieve.RagRetrievalService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/playground")
@RequiredArgsConstructor
public class PlaygroundController {

    private final ChatService chatService;
    private final PlaygroundService playgroundService;

    @PostMapping("/chat")
    public Object chat(@RequestBody PlaygroundChatRequest request) {
        if (request.getMessage() == null || request.getMessage().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "message must not be blank"));
        }
        if (request.getChatbotId() == null || request.getChatbotId().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "chatbotId must not be blank"));
        }

        final UUID widgetId;
        try {
            widgetId = UUID.fromString(request.getChatbotId());
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", "chatbotId must be a UUID"));
        }

        ChatRequest chatRequest = new ChatRequest();
        chatRequest.setMessage(request.getMessage());
        chatRequest.setSessionId(
                request.getSessionId() == null || request.getSessionId().isBlank()
                        ? UUID.randomUUID().toString()
                        : request.getSessionId()
        );
        Integer topK = request.getTopK();
        if (topK == null) {
            topK = RagRetrievalService.parseTopKOverride(request.getOverrideParams());
        }
        chatRequest.setTopK(topK);

        Double temperature = request.getTemperature();
        if (temperature == null) {
            temperature = LlmGenerationOptions.parseTemperatureOverride(request.getOverrideParams());
        }
        chatRequest.setTemperature(temperature);

        Integer maxTokens = request.getMaxTokens();
        if (maxTokens == null) {
            maxTokens = LlmGenerationOptions.parseMaxTokensOverride(request.getOverrideParams());
        }
        chatRequest.setMaxTokens(maxTokens);
        chatRequest.setPlaygroundDebugSources(true);

        return chatService.chatStream(chatRequest, widgetId);
    }

    @GetMapping("/sessions")
    public ResponseEntity<?> getSessions(@RequestParam(value = "chatbotId", required = false) String chatbotIdRaw) {
        if (chatbotIdRaw == null || chatbotIdRaw.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "chatbotId is required"));
        }
        final UUID chatbotId;
        try {
            chatbotId = UUID.fromString(chatbotIdRaw);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", "chatbotId must be a UUID"));
        }

        List<PlaygroundSessionResponse> sessions = playgroundService.listSessions(chatbotId);
        return ResponseEntity.ok(sessions);
    }

    @DeleteMapping("/sessions/{id}")
    public ResponseEntity<?> deleteSession(@PathVariable("id") String sessionIdRaw) {
        final UUID sessionId;
        try {
            sessionId = UUID.fromString(sessionIdRaw);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", "Invalid session id"));
        }

        try {
            playgroundService.deleteSession(sessionId);
            return ResponseEntity.ok(SimpleSuccessResponse.builder().success(true).build());
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(404).body(Map.of("message", "Session not found"));
        }
    }

    @PostMapping("/compare")
    public ResponseEntity<?> compare(@RequestBody PlaygroundCompareRequest request) {
        if (request.getMessage() == null || request.getMessage().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "message must not be blank"));
        }
        if (request.getChatbotId() == null || request.getChatbotId().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "chatbotId is required"));
        }
        final UUID chatbotId;
        try {
            chatbotId = UUID.fromString(request.getChatbotId());
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", "chatbotId must be a UUID"));
        }

        PlaygroundCompareResponse response = playgroundService.compare(
                chatbotId,
                request.getMessage(),
                request.getConfigA(),
                request.getConfigB()
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping("/export/{sessionId}")
    public ResponseEntity<?> exportSession(@PathVariable("sessionId") String sessionIdRaw) {
        final UUID sessionId;
        try {
            sessionId = UUID.fromString(sessionIdRaw);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", "Invalid session id"));
        }
        try {
            PlaygroundExportResponse response = playgroundService.exportSession(sessionId);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(404).body(Map.of("message", "Session not found"));
        }
    }
}
