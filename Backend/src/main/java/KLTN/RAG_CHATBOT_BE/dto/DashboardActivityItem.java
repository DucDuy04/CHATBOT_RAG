package KLTN.RAG_CHATBOT_BE.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class DashboardActivityItem {
    String id;
    String type;
    String actor;
    String target;
    String createdAt;
}
