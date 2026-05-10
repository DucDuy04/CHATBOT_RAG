package KLTN.RAG_CHATBOT_BE.dto;

import lombok.Data;

@Data
public class ChatbotCreateRequest {
    private String name;
    private String description;
    private String domain;
}
