package KLTN.RAG_CHATBOT_BE.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class DashboardTopChatbotItem {
    String id;
    String name;
    Long messageCount;
    Double satisfaction;
    String domain;
    String status;
}
