package KLTN.RAG_CHATBOT_BE.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class DocumentAssignRequest {
    private UUID chatbotId;
}
