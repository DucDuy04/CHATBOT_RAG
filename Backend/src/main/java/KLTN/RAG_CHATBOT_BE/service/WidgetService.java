package KLTN.RAG_CHATBOT_BE.service;

import KLTN.RAG_CHATBOT_BE.domain.widget.WidgetConfig;
import KLTN.RAG_CHATBOT_BE.domain.widget.WidgetConfigRepository;
import KLTN.RAG_CHATBOT_BE.dto.WidgetCreateRequest;
import KLTN.RAG_CHATBOT_BE.dto.WidgetCreateResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WidgetService {

    private final WidgetConfigRepository repository;

    public WidgetCreateResponse createWidgetConfig(WidgetCreateRequest request) {
        validateRequest(request);

        List<String> normalizedAllowedOrigin = request.getAllowedOrigin()
                .stream()
                .map(String::trim)
                .toList();

        WidgetConfig widget = WidgetConfig.builder()
                .name(request.getName().trim())
                .allowedOrigin(normalizedAllowedOrigin)
                .uiConfig(request.getUiConfig() == null ? Collections.emptyMap() : request.getUiConfig())
                .apiKey(UUID.randomUUID())
                .isActive(true)
                .build();

        WidgetConfig saved = repository.save(widget);

        return WidgetCreateResponse.builder()
                .widgetConfigId(saved.getId())
                .apiKey(saved.getApiKey())
                .name(saved.getName())
                .allowedOrigin(saved.getAllowedOrigin())
                .uiConfig(saved.getUiConfig())
                .uploadEndpoint("/api/documents/upload/" + saved.getId())
                .message("Tạo widgetConfig thành công. Dùng widgetConfigId này để ingest tài liệu.")
                .build();
    }

    private void validateRequest(WidgetCreateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request không được để trống.");
        }
        if (request.getName() == null || request.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("name là bắt buộc.");
        }
        if (request.getAllowedOrigin() == null || request.getAllowedOrigin().isEmpty()) {
            throw new IllegalArgumentException("allowedOrigin phải có ít nhất 1 domain.");
        }

        List<String> invalidOrigins = new ArrayList<>();
        for (String origin : request.getAllowedOrigin()) {
            if (origin == null || origin.trim().isEmpty()) {
                invalidOrigins.add(String.valueOf(origin));
            }
        }
        if (!invalidOrigins.isEmpty()) {
            throw new IllegalArgumentException("allowedOrigin chứa giá trị rỗng/không hợp lệ.");
        }
    }
}