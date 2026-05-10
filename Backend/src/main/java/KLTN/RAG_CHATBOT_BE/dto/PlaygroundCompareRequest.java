package KLTN.RAG_CHATBOT_BE.dto;

import lombok.Data;

import java.util.Map;

@Data
public class PlaygroundCompareRequest {
    private String chatbotId;
    private String message;
    private Map<String, Object> configA;
    private Map<String, Object> configB;
}
