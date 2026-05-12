package KLTN.RAG_CHATBOT_BE.dto;

import lombok.Data;

import java.util.Map;

@Data
public class ChatbotUpdateRequest {
    private String name;
    private String description;
    private String domain;
    private String status;
    private String systemPrompt;
    private Map<String, Object> modelConfig;
}
