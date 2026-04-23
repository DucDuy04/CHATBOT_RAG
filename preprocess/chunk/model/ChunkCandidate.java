package KLTN.RAG_CHATBOT_BE.preprocess.chunk.model;

import lombok.Builder;

import java.util.Map;

@Builder
public record ChunkCandidate(
        String content,
        int chunkIndex,
        Integer pageNumber,
        String section,
        Map<String, String> metadata
) {
}
