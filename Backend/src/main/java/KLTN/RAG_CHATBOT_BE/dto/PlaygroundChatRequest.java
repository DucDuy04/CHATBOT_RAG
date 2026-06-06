package KLTN.RAG_CHATBOT_BE.dto;

import lombok.Data;

import java.util.Map;

@Data
public class PlaygroundChatRequest {
    private String chatbotId;
    private String message;
    private String sessionId;
    private Map<String, Object> overrideParams;
    /** Optional; also accepted inside overrideParams.topK */
    private Integer topK;
    /** Optional; also accepted inside overrideParams.temperature */
    private Double temperature;
    /** Optional; also accepted inside overrideParams.maxTokens */
    private Integer maxTokens;
}
