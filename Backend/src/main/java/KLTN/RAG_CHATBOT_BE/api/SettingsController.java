package KLTN.RAG_CHATBOT_BE.api;

import KLTN.RAG_CHATBOT_BE.dto.SettingsApiKeyCreateRequest;
import KLTN.RAG_CHATBOT_BE.dto.SettingsApiKeyCreateResponse;
import KLTN.RAG_CHATBOT_BE.dto.SettingsApiKeyResponse;
import KLTN.RAG_CHATBOT_BE.dto.SettingsProfileResponse;
import KLTN.RAG_CHATBOT_BE.dto.SettingsProfileUpdateRequest;
import KLTN.RAG_CHATBOT_BE.dto.SimpleSuccessResponse;
import KLTN.RAG_CHATBOT_BE.service.SettingsService;
import KLTN.RAG_CHATBOT_BE.service.SettingsService.SettingsApiKeyNotFoundException;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final SettingsService settingsService;

    @GetMapping("/profile")
    public ResponseEntity<SettingsProfileResponse> getProfile() {
        return ResponseEntity.ok(settingsService.getProfile());
    }

    @PutMapping("/profile")
    public ResponseEntity<?> updateProfile(@RequestBody SettingsProfileUpdateRequest request) {
        try {
            return ResponseEntity.ok(settingsService.updateProfile(request));
        } catch (IllegalArgumentException e) {
            String msg = e.getMessage();
            if ("Invalid email".equals(msg)) {
                return ResponseEntity.badRequest().body(Map.of("message", "Invalid email"));
            }
            if ("language must be vi or en".equals(msg)) {
                return ResponseEntity.badRequest().body(Map.of("message", "language must be vi or en"));
            }
            return ResponseEntity.badRequest().body(Map.of("message", msg != null ? msg : "Bad request"));
        }
    }

    @GetMapping("/api-keys")
    public ResponseEntity<List<SettingsApiKeyResponse>> listApiKeys() {
        return ResponseEntity.ok(settingsService.listApiKeys());
    }

    @PostMapping("/api-keys")
    public ResponseEntity<SettingsApiKeyCreateResponse> createApiKey(@RequestBody(required = false) SettingsApiKeyCreateRequest request) {
        SettingsApiKeyCreateRequest body = request != null ? request : new SettingsApiKeyCreateRequest();
        return ResponseEntity.status(HttpStatus.CREATED).body(settingsService.createApiKey(body));
    }

    @DeleteMapping("/api-keys/{id}")
    public ResponseEntity<?> deleteApiKey(@PathVariable("id") String idRaw) {
        UUID id;
        try {
            id = UUID.fromString(idRaw.trim());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", "Invalid API key id"));
        }
        try {
            settingsService.deleteApiKey(id);
            return ResponseEntity.ok(SimpleSuccessResponse.builder().success(true).build());
        } catch (SettingsApiKeyNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "API key not found"));
        }
    }
}
