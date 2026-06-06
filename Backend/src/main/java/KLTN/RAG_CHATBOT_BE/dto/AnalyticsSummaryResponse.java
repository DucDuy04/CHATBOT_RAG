package KLTN.RAG_CHATBOT_BE.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AnalyticsSummaryResponse {
    Long totalMessages;
    Double totalMessagesDelta;
    Long uniqueSessions;
    Double uniqueSessionsDelta;
    Double fallbackRate;
    Double fallbackRateDelta;
}
