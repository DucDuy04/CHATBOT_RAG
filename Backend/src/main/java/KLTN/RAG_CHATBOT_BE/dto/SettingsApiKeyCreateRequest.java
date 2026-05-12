package KLTN.RAG_CHATBOT_BE.dto;

import lombok.Data;

@Data
public class SettingsApiKeyCreateRequest {
    /** Optional; FE always sends a non-empty default label. */
    private String name;
}
