package KLTN.RAG_CHATBOT_BE.dto;

import lombok.Data;

@Data
public class ChatRequest {
    private String sessionId; // ID phiên, FE tự tạo bằng UUID
    private String message; // Câu hỏi của người dùng
    /** Optional per-request Qdrant vector search limit (anchor top-K). Null → backend default. */
    private Integer topK;
    /** Optional LLM temperature. Null → modelConfig → backend default. */
    private Double temperature;
    /** Optional LLM max output tokens. Null → modelConfig → backend default. */
    private Integer maxTokens;
}