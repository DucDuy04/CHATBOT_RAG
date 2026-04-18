package KLTN.RAG_CHATBOT_BE.service;

import KLTN.RAG_CHATBOT_BE.domain.widget.WidgetConfig;
import KLTN.RAG_CHATBOT_BE.domain.widget.WidgetConfigRepository;
import KLTN.RAG_CHATBOT_BE.dto.WidgetCreateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WidgetService {

    private final WidgetConfigRepository repository;

    // Ngày 3: Tạo cấu hình cho 1 widget mới
    public WidgetConfig createWidget(WidgetCreateRequest request) {
        WidgetConfig widget = WidgetConfig.builder()
                .name(request.getName())
                .allowedOrigin(request.getAllowedOrigin())
                // .systemPrompt(request.getSystemPrompt())
                .uiConfig(request.getUiConfig())
                .apiKey(UUID.randomUUID()) // TỰ ĐỘNG SINH API KEY ĐỂ CẤP CHO CLIENT
                .isActive(true)
                .build();
        return repository.save(widget);
    }
}