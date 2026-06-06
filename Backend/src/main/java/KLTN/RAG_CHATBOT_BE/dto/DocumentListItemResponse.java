package KLTN.RAG_CHATBOT_BE.dto;

import lombok.Builder;
import lombok.Value;

import java.util.UUID;

@Value
@Builder
public class DocumentListItemResponse {
    UUID id;
    String fileName;
    Long fileSize;
    String fileType;
    String status;
    Integer chunkCount;
    UUID widgetConfigId;
}
