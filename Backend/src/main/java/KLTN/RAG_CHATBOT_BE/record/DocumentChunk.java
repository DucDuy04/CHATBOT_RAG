package KLTN.RAG_CHATBOT_BE.record;

public record DocumentChunk(
    String content,     // Nội dung đã được nhúng thêm Header
    String header,      // Metadata: Mục lục
    int startPage,      // Metadata: Trang bắt đầu
    int endPage         // Metadata: Trang kết thúc
) {}