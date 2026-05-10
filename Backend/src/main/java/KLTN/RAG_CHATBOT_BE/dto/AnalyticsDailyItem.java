package KLTN.RAG_CHATBOT_BE.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AnalyticsDailyItem {
    String date;
    Long messages;
    Long sessions;
}
