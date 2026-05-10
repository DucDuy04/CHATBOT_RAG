package KLTN.RAG_CHATBOT_BE.api;

import KLTN.RAG_CHATBOT_BE.dto.ChatbotCreateRequest;
import KLTN.RAG_CHATBOT_BE.dto.ChatbotPageResponse;
import KLTN.RAG_CHATBOT_BE.dto.ChatbotResponse;
import KLTN.RAG_CHATBOT_BE.dto.ChatbotUpdateRequest;
import KLTN.RAG_CHATBOT_BE.dto.EmbedConfigUpdateRequest;
import KLTN.RAG_CHATBOT_BE.dto.SimpleSuccessResponse;
import KLTN.RAG_CHATBOT_BE.service.WidgetService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/chatbots")
@RequiredArgsConstructor
public class ChatbotController {

    private final WidgetService widgetService;

    @GetMapping
    public ResponseEntity<ChatbotPageResponse> listChatbots(
            @RequestParam(required = false, defaultValue = "") String search,
            @RequestParam(required = false, defaultValue = "") String status,
            @RequestParam(required = false, defaultValue = "") String domain,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ResponseEntity.ok(widgetService.listChatbots(search, status, domain, page, size));
    }

    @PostMapping
    public ResponseEntity<?> createChatbot(@RequestBody ChatbotCreateRequest request) {
        try {
            ChatbotResponse created = widgetService.createChatbot(request);
            return ResponseEntity.ok(created);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getChatbot(@PathVariable("id") String idRaw) {
        UUID id;
        try {
            id = UUID.fromString(idRaw);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", "id không phải UUID hợp lệ."));
        }
        return widgetService.getChatbot(id)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Không tìm thấy chatbot.")));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateChatbot(
            @PathVariable("id") String idRaw,
            @RequestBody ChatbotUpdateRequest request
    ) {
        UUID id;
        try {
            id = UUID.fromString(idRaw);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", "id không phải UUID hợp lệ."));
        }
        try {
            return widgetService.updateChatbot(id, request)
                    .<ResponseEntity<?>>map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Không tìm thấy chatbot.")));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteChatbot(@PathVariable("id") String idRaw) {
        UUID id;
        try {
            id = UUID.fromString(idRaw);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", "id không phải UUID hợp lệ."));
        }
        boolean deleted = widgetService.softDeleteChatbot(id);
        if (!deleted) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Không tìm thấy chatbot."));
        }
        return ResponseEntity.ok(SimpleSuccessResponse.builder().success(true).build());
    }

    @GetMapping("/{id}/embed-config")
    public ResponseEntity<?> getEmbedConfig(@PathVariable("id") String idRaw) {
        UUID id;
        try {
            id = UUID.fromString(idRaw);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", "id không phải UUID hợp lệ."));
        }
        return widgetService.getEmbedConfig(id)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Không tìm thấy chatbot.")));
    }

    @PutMapping("/{id}/embed-config")
    public ResponseEntity<?> updateEmbedConfig(
            @PathVariable("id") String idRaw,
            @RequestBody EmbedConfigUpdateRequest request
    ) {
        UUID id;
        try {
            id = UUID.fromString(idRaw);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", "id không phải UUID hợp lệ."));
        }
        try {
            return widgetService.updateEmbedConfig(id, request)
                    .<ResponseEntity<?>>map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Không tìm thấy chatbot.")));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
}
