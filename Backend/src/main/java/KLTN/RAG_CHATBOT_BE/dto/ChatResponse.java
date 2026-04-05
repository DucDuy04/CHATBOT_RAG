package KLTN.RAG_CHATBOT_BE.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class ChatResponse {
    private String answer; // Câu trả lời từ GPT
    private List<SourceDto> sources; // Danh sách nguồn tài liệu

    @Data
    @Builder
    public static class SourceDto {
        private String fileName;
        private String chunkText; // Đoạn text gốc đã dùng làm context
    }
}