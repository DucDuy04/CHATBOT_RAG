package KLTN.RAG_CHATBOT_BE.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SettingsApiKeyResponse {
    private String id;
    private String name;
    private String maskedKey;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime lastUsedAt;
}
