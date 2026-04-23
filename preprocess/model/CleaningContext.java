package KLTN.RAG_CHATBOT_BE.preprocess.model;

import java.util.Map;

public record CleaningContext(
        String sourceName,
        String sourceType,
        int totalPages,
        Map<String, String> metadata
) {
}
