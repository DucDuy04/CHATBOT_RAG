package KLTN.RAG_CHATBOT_BE.preprocess.model;

import lombok.Builder;

import java.util.List;
import java.util.Map;

@Builder
public record ParsedPage(
        int pageNumber,
        String rawText,
        String cleanedText,
        List<String> tableBlocks,
        Map<String, String> metadata
) {
}
