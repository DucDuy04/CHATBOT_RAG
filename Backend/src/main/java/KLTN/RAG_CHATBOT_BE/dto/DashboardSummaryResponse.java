package KLTN.RAG_CHATBOT_BE.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class DashboardSummaryResponse {
    Long activeChatbots;
    Double activeChatbotsDelta;
    Long messages7d;
    Double messages7dDelta;
    Double avgSatisfaction;
    Double avgSatisfactionDelta;
    Long documentCount;
    Double documentCountDelta;
}
