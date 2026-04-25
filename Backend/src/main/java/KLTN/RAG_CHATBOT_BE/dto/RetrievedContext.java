package KLTN.RAG_CHATBOT_BE.dto;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class RetrievedContext {
    private UUID chunkId;
    private UUID documentId;
    private String fileName;
    private String content;
    private String chunkType;
    private String sectionId;
    private String sectionTitle;
    private String headingPathText;
    private Integer pageStart;
    private Integer pageEnd;
    private Double score;
}