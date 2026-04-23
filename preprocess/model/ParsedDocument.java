package KLTN.RAG_CHATBOT_BE.preprocess.model;

import lombok.Builder;

import java.util.List;
import java.util.Map;

@Builder
public record ParsedDocument(
        String sourceName,
        String sourceType,
        long sourceSize,
        List<ParsedPage> pages,
        Map<String, String> metadata
) {
}
