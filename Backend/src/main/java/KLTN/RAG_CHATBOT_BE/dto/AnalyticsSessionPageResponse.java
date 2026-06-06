package KLTN.RAG_CHATBOT_BE.dto;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class AnalyticsSessionPageResponse {
    List<AnalyticsSessionItem> items;
    Integer page;
    Integer size;
    Long total;
    Integer totalPages;
}
