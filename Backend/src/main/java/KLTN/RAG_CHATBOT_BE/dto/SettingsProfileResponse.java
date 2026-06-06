package KLTN.RAG_CHATBOT_BE.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SettingsProfileResponse {
    /** Display name — FE field {@code name}. */
    private String name;
    private String email;
    private String language;
    private SettingsNotificationsDto notifications;
}
