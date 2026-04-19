package KLTN.RAG_CHATBOT_BE.dto;

import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Value
@Builder
public class WidgetCreateResponse {
    UUID widgetConfigId;
    UUID apiKey;
    String name;
    List<String> allowedOrigin;
    Map<String, Object> uiConfig;
    String uploadEndpoint;
    String message;
}
