package KLTN.RAG_CHATBOT_BE.dto;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.List;

@Value
@Builder
public class PlaygroundExportResponse {
    String sessionId;
    String chatbotId;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
    List<PlaygroundMessageResponse> messages;
}
