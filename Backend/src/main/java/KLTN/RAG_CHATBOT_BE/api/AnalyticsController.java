package KLTN.RAG_CHATBOT_BE.api;

import KLTN.RAG_CHATBOT_BE.dto.AnalyticsByChatbotItem;
import KLTN.RAG_CHATBOT_BE.dto.AnalyticsDailyItem;
import KLTN.RAG_CHATBOT_BE.dto.AnalyticsSessionMessageResponse;
import KLTN.RAG_CHATBOT_BE.dto.AnalyticsSessionPageResponse;
import KLTN.RAG_CHATBOT_BE.dto.AnalyticsSummaryResponse;
import KLTN.RAG_CHATBOT_BE.dto.AnalyticsUnansweredItem;
import KLTN.RAG_CHATBOT_BE.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/summary")
    public ResponseEntity<?> getSummary(
            @RequestParam(value = "from", required = false) String fromRaw,
            @RequestParam(value = "to", required = false) String toRaw,
            @RequestParam(value = "chatbotId", required = false) String chatbotIdRaw
    ) {
        ValidationResult validation = validateRangeAndChatbot(fromRaw, toRaw, chatbotIdRaw);
        if (validation.error != null) {
            return ResponseEntity.badRequest().body(Map.of("message", validation.error));
        }
        AnalyticsSummaryResponse response = analyticsService.getSummary(
                validation.from,
                validation.to,
                validation.chatbotId
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping("/daily")
    public ResponseEntity<?> getDaily(
            @RequestParam(value = "from", required = false) String fromRaw,
            @RequestParam(value = "to", required = false) String toRaw,
            @RequestParam(value = "chatbotId", required = false) String chatbotIdRaw
    ) {
        ValidationResult validation = validateRangeAndChatbot(fromRaw, toRaw, chatbotIdRaw);
        if (validation.error != null) {
            return ResponseEntity.badRequest().body(Map.of("message", validation.error));
        }
        List<AnalyticsDailyItem> response = analyticsService.getDaily(
                validation.from,
                validation.to,
                validation.chatbotId
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping("/by-chatbot")
    public ResponseEntity<?> getByChatbot(
            @RequestParam(value = "from", required = false) String fromRaw,
            @RequestParam(value = "to", required = false) String toRaw
    ) {
        ValidationResult validation = validateRangeAndChatbot(fromRaw, toRaw, null);
        if (validation.error != null) {
            return ResponseEntity.badRequest().body(Map.of("message", validation.error));
        }
        List<AnalyticsByChatbotItem> response = analyticsService.getByChatbot(validation.from, validation.to);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/unanswered")
    public ResponseEntity<?> getUnanswered(@RequestParam(value = "limit", required = false) String limitRaw) {
        Integer limit = parseLimit(limitRaw);
        if (limit == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "limit must be between 1 and 100"));
        }
        List<AnalyticsUnansweredItem> response = analyticsService.getUnanswered(limit);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/sessions")
    public ResponseEntity<?> getSessions(
            @RequestParam(value = "from", required = false) String fromRaw,
            @RequestParam(value = "to", required = false) String toRaw,
            @RequestParam(value = "chatbotId", required = false) String chatbotIdRaw,
            @RequestParam(value = "page", required = false) String pageRaw,
            @RequestParam(value = "size", required = false) String sizeRaw
    ) {
        ValidationResult validation = validateRangeAndChatbot(fromRaw, toRaw, chatbotIdRaw);
        if (validation.error != null) {
            return ResponseEntity.badRequest().body(Map.of("message", validation.error));
        }

        Integer page = parsePage(pageRaw);
        if (page == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "page must be >= 0"));
        }
        Integer size = parseSize(sizeRaw);
        if (size == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "size must be between 1 and 100"));
        }

        AnalyticsSessionPageResponse response = analyticsService.getSessions(
                validation.from,
                validation.to,
                validation.chatbotId,
                page,
                size
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping("/sessions/{id}/messages")
    public ResponseEntity<?> getSessionMessages(@PathVariable("id") String sessionIdRaw) {
        final UUID sessionKey;
        try {
            sessionKey = UUID.fromString(sessionIdRaw);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", "Invalid session id"));
        }
        try {
            List<AnalyticsSessionMessageResponse> response = analyticsService.getSessionMessages(sessionKey);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(404).body(Map.of("message", "Session not found"));
        }
    }

    private ValidationResult validateRangeAndChatbot(String fromRaw, String toRaw, String chatbotIdRaw) {
        if (fromRaw == null || fromRaw.isBlank() || toRaw == null || toRaw.isBlank()) {
            return ValidationResult.error("Invalid date format. Expected YYYY-MM-DD");
        }

        final LocalDate from;
        final LocalDate to;
        try {
            from = LocalDate.parse(fromRaw.trim());
            to = LocalDate.parse(toRaw.trim());
        } catch (DateTimeParseException ex) {
            return ValidationResult.error("Invalid date format. Expected YYYY-MM-DD");
        }

        if (from.isAfter(to)) {
            return ValidationResult.error("from must be before or equal to to");
        }

        UUID chatbotId = null;
        if (chatbotIdRaw != null && !chatbotIdRaw.isBlank()) {
            try {
                chatbotId = UUID.fromString(chatbotIdRaw.trim());
            } catch (IllegalArgumentException ex) {
                return ValidationResult.error("chatbotId must be a UUID");
            }
        }

        return ValidationResult.ok(from, to, chatbotId);
    }

    private Integer parseLimit(String limitRaw) {
        if (limitRaw == null || limitRaw.isBlank()) {
            return 10;
        }
        try {
            int limit = Integer.parseInt(limitRaw.trim());
            if (limit < 1 || limit > 100) {
                return null;
            }
            return limit;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Integer parsePage(String pageRaw) {
        if (pageRaw == null || pageRaw.isBlank()) {
            return 0;
        }
        try {
            int page = Integer.parseInt(pageRaw.trim());
            return page < 0 ? null : page;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Integer parseSize(String sizeRaw) {
        if (sizeRaw == null || sizeRaw.isBlank()) {
            return 10;
        }
        try {
            int size = Integer.parseInt(sizeRaw.trim());
            return (size < 1 || size > 100) ? null : size;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private record ValidationResult(LocalDate from, LocalDate to, UUID chatbotId, String error) {
        static ValidationResult ok(LocalDate from, LocalDate to, UUID chatbotId) {
            return new ValidationResult(from, to, chatbotId, null);
        }

        static ValidationResult error(String message) {
            return new ValidationResult(null, null, null, message);
        }
    }
}
