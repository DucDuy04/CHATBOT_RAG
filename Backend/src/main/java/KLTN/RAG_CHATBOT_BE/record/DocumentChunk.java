package KLTN.RAG_CHATBOT_BE.record;

/**
 * Pipeline DTO đại diện cho một chunk sau khi chunkingService xử lý section.
 *
 * @param content          Nội dung chunk (text thuần hoặc markdown bảng)
 * @param header           Tiêu đề section chứa chunk này
 * @param startPage        Trang bắt đầu
 * @param endPage          Trang kết thúc
 * @param chunkType        "text" | "table_summary" | "table_row_group" | "section_summary"
 * @param sectionId        ID cấu trúc của section (vd "sec_6.2" hoặc "sec_idx_3")
 * @param parentId         ID section cha (vd "parent_6")
 * @param tableId          ID bảng nếu chunk là table chunk (vd "tbl_2_5"), null nếu là text
 * @param headingPathText  Chuỗi breadcrumb heading (vd "6 Auth > 6.2 Token")
 * @param orderIndex       Thứ tự chunk toàn cục trong tài liệu (0-based)
 * @param tokenCount       Ước tính số token (content.length / 4)
 * @param sectionOrder     Thứ tự section trong tài liệu (0-based) — dùng để sort lại đúng thứ tự trong Qdrant
 * @param headingLevel     Cấp độ heading (1=top-level, 2=subsection, 0=không có số)
 * @param childSectionIds  Comma-separated sectionIds của các subsection trực tiếp.
 *                         Chỉ có giá trị khi chunkType="parent_section_summary", null với các chunk khác.
 */
public record DocumentChunk(
    String content,
    String header,
    int startPage,
    int endPage,
    String chunkType,
    String sectionId,
    String parentId,
    String tableId,
    String headingPathText,
    int orderIndex,
    int tokenCount,
    int sectionOrder,
    int headingLevel,
    String childSectionIds
) {}
