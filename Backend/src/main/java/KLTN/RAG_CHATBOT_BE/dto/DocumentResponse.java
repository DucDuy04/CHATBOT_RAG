package KLTN.RAG_CHATBOT_BE.dto;

import lombok.Builder;
import lombok.Value;

import java.util.UUID;

@Value
@Builder
public class DocumentResponse {
    UUID id;
    String filename;
    String type;
    UUID chatbotId;
    String chatbotName;
    Integer chunkCount;
    Long sizeBytes;
    String status;
    Integer progress;
    String uploadedAt;
    String error;
}
