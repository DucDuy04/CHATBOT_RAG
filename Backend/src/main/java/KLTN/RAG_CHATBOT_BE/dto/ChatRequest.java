package KLTN.RAG_CHATBOT_BE.dto;

import lombok.Data;

@Data
public class ChatRequest {
    private String sessionId; // ID phiên, FE tự tạo bằng UUID
    private String message; // Câu hỏi của người dùng
}