package KLTN.RAG_CHATBOT_BE.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ChatFeedbackResponse {
    boolean success;
    String messageId;
    Integer rating;
    String comment;
}
