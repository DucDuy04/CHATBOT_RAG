package KLTN.RAG_CHATBOT_BE.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DocumentUploadResponse {
    private Long id;
    private String fileName;
    private String status;
    private String message;
}