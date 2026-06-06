package KLTN.RAG_CHATBOT_BE.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AnalyticsByChatbotItem {
    String chatbotId;
    String chatbotName;
    Long messageCount;
    Double share;
}
