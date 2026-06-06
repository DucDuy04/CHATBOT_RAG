package KLTN.RAG_CHATBOT_BE.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Value;

import java.util.Map;

@Value
@Builder
public class ChatbotResponse {
    String id;
    String name;
    String description;
    String domain;
    Integer documentCount;
    Integer messageCount;
    String status;
    String updatedAt;
    String initials;
    String systemPrompt;
    Map<String, Object> modelConfig;

    /** Chỉ set khi tạo mới — không trả trong list/detail để tránh lộ key. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    String apiKey;
}
