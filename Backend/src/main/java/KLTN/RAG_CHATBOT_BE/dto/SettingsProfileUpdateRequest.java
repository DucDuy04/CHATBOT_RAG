package KLTN.RAG_CHATBOT_BE.dto;

import lombok.Data;

@Data
public class SettingsProfileUpdateRequest {
    private String name;
    private String email;
    private String language;
    private SettingsNotificationsDto notifications;
}
