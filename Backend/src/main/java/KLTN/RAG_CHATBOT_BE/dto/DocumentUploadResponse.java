package KLTN.RAG_CHATBOT_BE.dto;

import java.util.UUID;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DocumentUploadResponse {
    private UUID id;
    private String fileName;
    private String status;
    private String message;
}