package KLTN.RAG_CHATBOT_BE.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AnalyticsSessionSourceResponse {
    String fileName;
    String documentName;
    String title;
    Double score;
    String snippet;
    String chunk;
    String content;
    String pages;
}
