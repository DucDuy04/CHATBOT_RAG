package KLTN.RAG_CHATBOT_BE.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class DocumentChunkResponse {
    Integer chunkIndex;
    String content;
    Integer tokenCount;
}
