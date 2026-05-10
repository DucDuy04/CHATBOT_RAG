package KLTN.RAG_CHATBOT_BE.dto;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class EmbedConfigResponse {
    String widgetColor;
    String welcomeMessage;
    String position;
    List<String> allowedOrigins;
    String launcherIcon;
}
