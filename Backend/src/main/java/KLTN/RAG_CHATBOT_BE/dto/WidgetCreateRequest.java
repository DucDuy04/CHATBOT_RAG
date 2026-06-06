package KLTN.RAG_CHATBOT_BE.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class WidgetCreateRequest {
    private String name;
    private List<String> allowedOrigin;
    private String systemPrompt;
    private Map<String, Object> uiConfig;
}