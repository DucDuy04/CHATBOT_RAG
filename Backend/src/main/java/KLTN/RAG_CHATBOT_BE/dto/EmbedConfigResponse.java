package KLTN.RAG_CHATBOT_BE.dto;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class EmbedConfigResponse {
    /**
     * Public embed UUID (same as {@code WidgetConfig.apiKey}) for widget script / {@code x-api-key}.
     * Not related to Settings API keys.
     */
    String widgetKey;
    String widgetColor;
    String welcomeMessage;
    String position;
    List<String> allowedOrigins;
    String launcherIcon;
}
