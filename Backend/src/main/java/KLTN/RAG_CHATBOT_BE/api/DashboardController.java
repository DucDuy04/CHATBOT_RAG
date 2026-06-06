package KLTN.RAG_CHATBOT_BE.api;

import KLTN.RAG_CHATBOT_BE.dto.DashboardActivityItem;
import KLTN.RAG_CHATBOT_BE.dto.DashboardMessageVolumeItem;
import KLTN.RAG_CHATBOT_BE.dto.DashboardSummaryResponse;
import KLTN.RAG_CHATBOT_BE.dto.DashboardTopChatbotItem;
import KLTN.RAG_CHATBOT_BE.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/summary")
    public ResponseEntity<DashboardSummaryResponse> getSummary() {
        return ResponseEntity.ok(dashboardService.getSummary());
    }

    @GetMapping("/message-volume")
    public ResponseEntity<?> getMessageVolume(@RequestParam(value = "days", required = false) String daysRaw) {
        Integer days = parseIntOrDefault(daysRaw, 7);
        if (days == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "days must be a number"));
        }
        if (days < 1 || days > 90) {
            return ResponseEntity.badRequest().body(Map.of("message", "days must be between 1 and 90"));
        }
        List<DashboardMessageVolumeItem> response = dashboardService.getMessageVolume(days);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/top-chatbots")
    public ResponseEntity<?> getTopChatbots(@RequestParam(value = "limit", required = false) String limitRaw) {
        Integer limit = parseIntOrDefault(limitRaw, 5);
        if (limit == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "limit must be a number"));
        }
        if (limit < 1 || limit > 20) {
            return ResponseEntity.badRequest().body(Map.of("message", "limit must be between 1 and 20"));
        }
        List<DashboardTopChatbotItem> response = dashboardService.getTopChatbots(limit);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/activity")
    public ResponseEntity<?> getActivity(@RequestParam(value = "limit", required = false) String limitRaw) {
        Integer limit = parseIntOrDefault(limitRaw, 20);
        if (limit == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "limit must be a number"));
        }
        if (limit < 1 || limit > 100) {
            return ResponseEntity.badRequest().body(Map.of("message", "limit must be between 1 and 100"));
        }
        List<DashboardActivityItem> response = dashboardService.getActivity(limit);
        return ResponseEntity.ok(response);
    }

    private Integer parseIntOrDefault(String value, int defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
