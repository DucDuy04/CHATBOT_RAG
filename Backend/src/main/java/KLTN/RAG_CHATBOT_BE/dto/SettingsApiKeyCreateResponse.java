package KLTN.RAG_CHATBOT_BE.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Matches FE {@code settingsApi.generateApiKey} — {@code key} + {@code plainTextKey}. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SettingsApiKeyCreateResponse {
    private SettingsApiKeyResponse key;
    private String plainTextKey;
}
