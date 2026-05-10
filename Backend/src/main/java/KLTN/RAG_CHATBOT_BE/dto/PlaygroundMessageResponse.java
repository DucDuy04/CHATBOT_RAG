package KLTN.RAG_CHATBOT_BE.dto;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Value
@Builder
public class PlaygroundMessageResponse {
    String id;
    String role;
    String content;
    LocalDateTime createdAt;
    List<Map<String, Object>> sources;
}
