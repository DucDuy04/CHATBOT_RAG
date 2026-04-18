package KLTN.RAG_CHATBOT_BE.api;

import KLTN.RAG_CHATBOT_BE.domain.widget.WidgetConfig;
import KLTN.RAG_CHATBOT_BE.dto.WidgetCreateRequest;
import KLTN.RAG_CHATBOT_BE.service.WidgetService;
import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/widgets")
@RequiredArgsConstructor
public class WidgetController {

    private final WidgetService widgetService;

    // Tạo widget mới (Lấy API Key sau khi gọi API này)
    @PostMapping
    public ResponseEntity<WidgetConfig> createWidget(@RequestBody WidgetCreateRequest request) {
        return ResponseEntity.ok(widgetService.createWidget(request));
    }
}