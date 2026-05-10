package KLTN.RAG_CHATBOT_BE.dto;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class DocumentPageResponse {
    List<DocumentResponse> items;
    int page;
    int size;
    long total;
    int totalPages;
}
