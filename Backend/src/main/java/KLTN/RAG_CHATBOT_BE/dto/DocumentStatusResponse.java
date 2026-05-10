package KLTN.RAG_CHATBOT_BE.dto;

import lombok.Builder;
import lombok.Value;

import java.util.UUID;

@Value
@Builder
public class DocumentStatusResponse {
    UUID id;
    String status;
    Integer progress;
    Integer chunkCount;
    String error;
}
