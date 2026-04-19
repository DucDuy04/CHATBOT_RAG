package KLTN.RAG_CHATBOT_BE.api;

import KLTN.RAG_CHATBOT_BE.dto.WidgetCreateRequest;
import KLTN.RAG_CHATBOT_BE.dto.WidgetCreateResponse;
import KLTN.RAG_CHATBOT_BE.service.WidgetService;
import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/widgets")
@RequiredArgsConstructor
public class WidgetController {

    private final WidgetService widgetService;

    // Tạo widget config để dùng cho ingest tài liệu theo widget
    @PostMapping
    public ResponseEntity<WidgetCreateResponse> createWidget(@RequestBody WidgetCreateRequest request) {
        return ResponseEntity.ok(widgetService.createWidgetConfig(request));
    }
}