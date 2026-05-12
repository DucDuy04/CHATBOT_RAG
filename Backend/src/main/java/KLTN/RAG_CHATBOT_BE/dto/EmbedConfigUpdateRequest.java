package KLTN.RAG_CHATBOT_BE.dto;

import lombok.Data;

import java.util.List;

@Data
public class EmbedConfigUpdateRequest {
    private String widgetColor;
    private String welcomeMessage;
    private String position;
    private List<String> allowedOrigins;
    private String launcherIcon;
}
