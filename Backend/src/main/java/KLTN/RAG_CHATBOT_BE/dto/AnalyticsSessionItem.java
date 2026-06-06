package KLTN.RAG_CHATBOT_BE.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AnalyticsSessionItem {
    String id;
    String chatbotId;
    String chatbotName;
    Long messageCount;
    String createdAt;
}
