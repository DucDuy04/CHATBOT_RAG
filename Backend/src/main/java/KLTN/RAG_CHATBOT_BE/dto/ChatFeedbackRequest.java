package KLTN.RAG_CHATBOT_BE.dto;

import lombok.Data;

@Data
public class ChatFeedbackRequest {
    private String messageId;
    private Integer rating;
    private String comment;
}
