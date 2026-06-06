package KLTN.RAG_CHATBOT_BE.dto;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class AnalyticsSessionMessageResponse {
    String id;
    String role;
    String content;
    String createdAt;
    List<AnalyticsSessionSourceResponse> sources;
}
