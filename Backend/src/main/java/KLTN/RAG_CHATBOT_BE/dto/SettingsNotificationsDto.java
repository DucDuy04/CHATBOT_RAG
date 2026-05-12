package KLTN.RAG_CHATBOT_BE.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SettingsNotificationsDto {
    private Boolean embeddingFailed;
    private Boolean dailySummary;
    private Boolean newFeedback;
}
