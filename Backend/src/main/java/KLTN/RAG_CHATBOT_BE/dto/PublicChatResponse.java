package KLTN.RAG_CHATBOT_BE.dto;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class PublicChatResponse {
    String answer;
    String sessionId;
    List<ChatResponse.SourceDto> sources;
}
