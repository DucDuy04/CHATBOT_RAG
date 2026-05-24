package KLTN.RAG_CHATBOT_BE.record;

/**
 * Pipeline DTO đại diện cho một chunk sau khi chunkingService xử lý section.
 *
 * @param content          Nội dung chunk (canonical text cho normalized_table_row)
 * @param header           Tiêu đề section chứa chunk này
 * @param startPage        Trang bắt đầu
 * @param endPage          Trang kết thúc
 * @param chunkType        text | normalized_table_row | table_summary | section_summary | ...
 * @param sectionId        ID cấu trúc của section
 * @param parentId         ID section cha
 * @param tableId          ID bảng nếu chunk là table chunk
 * @param headingPathText  Chuỗi breadcrumb heading
 * @param orderIndex       Thứ tự chunk toàn cục
 * @param tokenCount       Ước tính token
 * @param sectionOrder     Thứ tự section
 * @param headingLevel     Cấp heading
 * @param childSectionIds  Con trực tiếp (parent_section_summary)
 * @param tableName        Tên logical table (normalized_table_row / table_summary)
 * @param rowIndex         Chỉ số dòng (normalized_table_row)
 * @param cellsJson        JSON map header→value cho Qdrant/DB
 * @param groupContext     Ngữ cảnh nhóm (normalized_table_row)
 * @param rowPageStart     Trang của dòng (normalized_table_row)
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
    String childSectionIds,
    String tableName,
    Integer rowIndex,
    String cellsJson,
    String groupContext,
    Integer rowPageStart
) {
    /** Backward-compatible factory without table row metadata. */
    public static DocumentChunk of(
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
    ) {
        return new DocumentChunk(
                content, header, startPage, endPage, chunkType,
                sectionId, parentId, tableId, headingPathText,
                orderIndex, tokenCount, sectionOrder, headingLevel, childSectionIds,
                null, null, null, null, null);
    }
}
