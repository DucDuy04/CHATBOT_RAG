package KLTN.RAG_CHATBOT_BE.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AnalyticsUnansweredItem {
    String id;
    String question;
    String chatbotName;
    Long count;
}
