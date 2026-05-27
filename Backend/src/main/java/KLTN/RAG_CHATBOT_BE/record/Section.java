package KLTN.RAG_CHATBOT_BE.record;

import KLTN.RAG_CHATBOT_BE.service.RawTableBlock;
import java.util.List;

/**
 * Pipeline DTO đại diện cho một section trong tài liệu sau khi parse.
 *
 * @param header        Tiêu đề heading của section (ví dụ "6.2 Authentication")
 * @param startPage     Trang bắt đầu (1-based)
 * @param endPage       Trang kết thúc (1-based)
 * @param content       Nội dung text/table của section (có thể rỗng nếu section chỉ là heading cha)
 * @param orderIndex    Thứ tự section trong tài liệu (0-based), dùng để sort và lưu metadata vào Qdrant
 * @param headingLevel  Cấp độ heading (1=top-level "1. xxx", 2="1.2 xxx", 3="1.2.3 xxx", 0=không có số)
 */
public record Section(
    String header,
    int startPage,
    int endPage,
    String content,
    int orderIndex,
    int headingLevel,
    List<RawTableBlock> rawTableBlocks
) {
    public Section(
            String header,
            int startPage,
            int endPage,
            String content,
            int orderIndex,
            int headingLevel
    ) {
        this(header, startPage, endPage, content, orderIndex, headingLevel, List.of());
    }

    public Section {
        rawTableBlocks = rawTableBlocks == null ? List.of() : List.copyOf(rawTableBlocks);
    }
}
