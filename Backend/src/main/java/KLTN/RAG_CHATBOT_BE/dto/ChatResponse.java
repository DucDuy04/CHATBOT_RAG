package KLTN.RAG_CHATBOT_BE.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class ChatResponse {
    private String answer; // Câu trả lời từ GPT
    private List<SourceDto> sources; // Danh sách nguồn tài liệu

    @Data
    @Builder
    public static class SourceDto {
        private String fileName;
        private String sectionTitle;
        private String pages;
        private String chunkType;
        private String chunkText;
        private String rawChunkText;
        private Map<String, String> displayCells;
        private Map<String, String> rawCells;
    }
}
