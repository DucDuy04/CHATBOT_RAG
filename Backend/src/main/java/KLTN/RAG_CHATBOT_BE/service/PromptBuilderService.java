package KLTN.RAG_CHATBOT_BE.service;

import KLTN.RAG_CHATBOT_BE.domain.chat.ChatMessage;
import KLTN.RAG_CHATBOT_BE.dto.RetrievedContext;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
public class PromptBuilderService {

    /**
     * Nhắc thêm khi đã biết context có chunk bảng — tránh LLM từ chối dù evidence có trong Source.
     */
    private static final String TABLE_LOOKUP_TABLE_SOURCES_PRESENT_NOTE = """
            [NHẮN NGỮ CẢNH BẢNG: Trong danh sách Source bên dưới có ít nhất một mục loại bảng (table_summary / normalized_table_row / table_row_group) hoặc nội dung dạng bảng Markdown. Bạn phải đọc các Source đó trước khi kết luận không có thông tin.]
            """;

    private static final String NORMALIZED_ROW_SOURCE_NOTE = """
            [DÒNG BẢNG CHUẨN HÓA: Có Source Type=normalized_table_row — mỗi Source là MỘT dòng bảng với các cột (Key: giá trị). Trả lời trực tiếp từ giá trị cột của đúng dòng đó; không trộn dữ liệu giữa các dòng hoặc các nhóm khác nhau.]
            """;

    private static final String SYSTEM_PROMPT = """
        Bạn là trợ lý AI chuyên trả lời câu hỏi dựa trên tài liệu được cung cấp.

        ═══════════════════════════════════════════
        NGUYÊN TẮC CỐT LÕI
        ═══════════════════════════════════════════
        1. CHỈ sử dụng nội dung trong [TÀI LIỆU THAM KHẢO] để trả lời. KHÔNG dùng kiến thức ngoài tài liệu.
        2. Nếu [TÀI LIỆU THAM KHẢO] hoàn toàn rỗng hoặc không có Source nào liên quan, trả lời đúng 1 câu: "Tôi không tìm thấy thông tin này trong tài liệu."
        2b. Nếu có ít nhất một Source liên quan nhưng chỉ trả lời được một phần (vd: câu đếm/liệt kê mà context chưa đủ), KHÔNG khẳng định toàn bộ tài liệu chỉ có phần đó. Hãy trả lời theo nội dung đã truy xuất và nêu rõ phạm vi (vd: "Trong phần tài liệu được truy xuất, tôi thấy các mục sau: ...").
        3. KHÔNG suy diễn, KHÔNG bịa đặt, KHÔNG ngoại suy ngoài những gì tài liệu nói rõ.
        4. Trả lời bằng tiếng Việt, ngắn gọn, đúng trọng tâm.

        ═══════════════════════════════════════════
        CHECKLIST TRƯỚC KHI TRẢ LỜI (bắt buộc kiểm tra)
        ═══════════════════════════════════════════
        Trước khi viết câu trả lời, hãy tự hỏi:
        □ Câu hỏi đang hỏi section cha hay section con hay cả hai?
        □ Có Source nào có Type=section_summary không? Nếu có, đọc kỹ trước.
        □ Câu hỏi yêu cầu liệt kê đầy đủ không? Nếu có, đã duyệt hết tất cả Source chưa?
        □ Câu hỏi yêu cầu đếm? Nếu có, đã xác định được tập đối tượng chưa?
        □ Có bảng nào liên quan không? Nếu có, có Source Type=table_summary hoặc table_row_group không?
        □ Nguồn trích dẫn có thật sự chứa nội dung trả lời không?
        □ Có nội dung nào bị bỏ sót do chỉ đọc một phần Source không?

        ═══════════════════════════════════════════
        XỬ LÝ SECTION VÀ SUBSECTION
        ═══════════════════════════════════════════
        5. Khi có Source có Type=section_summary, đây là tóm tắt của toàn bộ section — đọc kỹ trước khi đọc chi tiết.
        6. KHÔNG nói "không tìm thấy" chỉ vì một Source cha có content rỗng. Phải kiểm tra các Source con (subsection).
        7. Nếu câu hỏi hỏi về section cha (vd: "Section 6 gồm những gì?"), hãy tổng hợp từ TẤT CẢ Source thuộc section đó và các subsection con.
        8. Khi tổng hợp nhiều subsection, giữ nguyên cấu trúc nhóm logic như trong tài liệu.

        ═══════════════════════════════════════════
        XỬ LÝ BẢNG
        ═══════════════════════════════════════════
        9. Khi có Source có Type=table_summary hoặc table_row_group, hoặc Content chứa '|' theo dạng Markdown table, BẮT BUỘC trình bày kết quả bằng bảng Markdown.
        10. Đọc đúng từng hàng và cột. KHÔNG nhầm lẫn dữ liệu giữa các hàng.
        11. Nếu dữ liệu bảng nằm ở nhiều Source, BẮT BUỘC gộp tất cả hàng thành MỘT bảng duy nhất, KHÔNG bỏ sót hàng nào.
        12. KHÔNG dùng table_summary làm nguồn duy nhất nếu câu hỏi yêu cầu số liệu cụ thể — phải đọc thêm table_row_group.
        13. Nếu table_summary cho biết có nhiều dòng nhưng chỉ thấy một phần, hãy nói rõ: "Bảng có N dòng, hiển thị M dòng được tìm thấy."
        14. Khi câu hỏi nêu RÕ tên một bảng (vd: PhongKhaoThi, SinhVien), CHỈ trả lời cột/dòng thuộc đúng bảng đó; KHÔNG trộn cột từ bảng khác (GiangVien, MonHoc, ...). Nếu một Source chứa nhiều bảng, chỉ dùng phần ngay sau tiêu đề/heading của bảng được hỏi cho đến trước heading bảng tiếp theo.
        15. Khi có Source có Type=text_table_like, nội dung có cấu trúc dạng bảng nhưng không được parser nhận diện thành bảng chính thức. BẮT BUỘC giữ nguyên quan hệ hàng-cột, KHÔNG flatten thành danh sách phẳng. Trình bày theo dạng "thuộc tính — giá trị" hoặc bảng Markdown nếu có thể nhận ra cấu trúc cột.

        ═══════════════════════════════════════════
        XỬ LÝ CÂU HỎI ĐẾM / BAO NHIÊU
        ═══════════════════════════════════════════
        16. Với câu hỏi "bao nhiêu", "có mấy", "tổng số": KHÔNG tự đoán số. Phải đếm thực sự từ danh sách.
        17. Quy trình đếm bắt buộc:
            a. Xác định tập đối tượng cần đếm (từ tất cả Source liên quan).
            b. Chuẩn hóa danh sách (bỏ duplicate, bỏ item không hợp lệ).
            c. Đếm số lượng.
            d. Trả lời: "Có N [đối tượng]. Danh sách: 1. ..., 2. ..., ..."
        18. Nếu phạm vi mơ hồ (vd: tính trong một section hay toàn tài liệu), trả lời theo cả hai cách hiểu.

        ═══════════════════════════════════════════
        XỬ LÝ DANH SÁCH / LIỆT KÊ
        ═══════════════════════════════════════════
        19. Khi được yêu cầu liệt kê, tổng hợp ĐẦY ĐỦ tất cả items từ tất cả Source. KHÔNG tự ý lược bớt.
        20. Sắp xếp theo thứ tự trong tài liệu (không sắp xếp lại theo bảng chữ cái trừ khi được yêu cầu).
        21. Nếu một item xuất hiện nhiều lần ở nhiều Source, chỉ liệt kê 1 lần (deduplicate).


        ═══════════════════════════════════════════
        GIỚI HẠN ĐỘ DÀI
        ═══════════════════════════════════════════
        22. Câu hỏi thực thể đơn giản: trả lời ngắn gọn (1-3 câu + nguồn).
        23. Câu hỏi liệt kê / tóm tắt section: trả lời đầy đủ, có thể dài, nhưng không dài hơn mức cần thiết.
        24. Câu hỏi đếm: luôn kèm danh sách để người dùng kiểm chứng.
        """;

    public String getSystemPrompt() {
        return SYSTEM_PROMPT;
    }

    /**
     * Xây dựng user prompt từ danh sách RetrievedContext, kèm loại câu hỏi để LLM
     * biết chiến lược trả lời phù hợp.
     */
    public String buildUserPromptFromRetrievedContexts(
            String question,
            List<KLTN.RAG_CHATBOT_BE.dto.RetrievedContext> contexts,
            List<ChatMessage> chatHistory
    ) {
        return buildUserPromptFromRetrievedContexts(question, contexts, chatHistory, null);
    }

    public String buildUserPromptFromRetrievedContexts(
            String question,
            List<KLTN.RAG_CHATBOT_BE.dto.RetrievedContext> contexts,
            List<ChatMessage> chatHistory,
            String queryTypeHint
    ) {
        return buildUserPromptFromRetrievedContexts(question, contexts, chatHistory, queryTypeHint, null);
    }

    public String buildUserPromptFromRetrievedContexts(
            String question,
            List<KLTN.RAG_CHATBOT_BE.dto.RetrievedContext> contexts,
            List<ChatMessage> chatHistory,
            String queryTypeHint,
            String lockedScopeLabel
    ) {
        StringBuilder prompt = new StringBuilder();

        // Khi scope đã được lock vào một section cụ thể, thông báo cho LLM để tránh cite ngoài phạm vi.
        if (lockedScopeLabel != null && !lockedScopeLabel.isBlank()) {
            prompt.append("[PHẠM VI TÌM KIẾM ĐÃ XÁC ĐỊNH: ").append(lockedScopeLabel).append("]\n");
            prompt.append("Tất cả Source dưới đây đều thuộc section này và các mục con của nó. ")
                  .append("CHỈ cite từ các Source trong danh sách, KHÔNG cite section khác.\n\n");
        }

        // Nếu có query type hint, thêm instruction đặc biệt trước [TÀI LIỆU THAM KHẢO]
        if (queryTypeHint != null && !queryTypeHint.isBlank()) {
            prompt.append("[LOẠI CÂU HỎI: ").append(queryTypeHint).append("]\n");
            String typeInstruction = buildQueryTypeInstruction(queryTypeHint);
            prompt.append(typeInstruction);
            if ("TABLE_LOOKUP".equalsIgnoreCase(queryTypeHint.trim()) && contextsContainTableLikeChunks(contexts)) {
                prompt.append("\n").append(TABLE_LOOKUP_TABLE_SOURCES_PRESENT_NOTE.trim());
            }
            prompt.append("\n\n");
        }

        if (contextsContainNormalizedTableRow(contexts)) {
            prompt.append(NORMALIZED_ROW_SOURCE_NOTE.trim()).append("\n\n");
        }

        prompt.append("[TÀI LIỆU THAM KHẢO]\n");

        for (int i = 0; i < contexts.size(); i++) {
            RetrievedContext ctx = contexts.get(i);

            prompt.append("[Source ").append(i + 1).append("]\n");
            prompt.append("Document: ").append(nullSafe(ctx.getFileName())).append("\n");
            prompt.append("Section: ").append(nullSafe(ctx.getHeadingPathText())).append("\n");
            prompt.append("Pages: ").append(ctx.getPageStart()).append("-").append(ctx.getPageEnd()).append("\n");
            prompt.append("Type: ").append(nullSafe(ctx.getChunkType())).append("\n\n");
            prompt.append("Content:\n");
            prompt.append(ctx.getContent()).append("\n\n");
        }

        if (!chatHistory.isEmpty()) {
            prompt.append("[LỊCH SỬ HỘI THOẠI]\n");
            for (ChatMessage msg : chatHistory) {
                String role = "USER".equals(msg.getRole().name().toUpperCase(Locale.ROOT))
                        ? "Người dùng" : "Trợ lý";
                prompt.append(role).append(": ").append(msg.getContent()).append("\n");
            }
            prompt.append("\n");
        }

        prompt.append("[CÂU HỎI HIỆN TẠI]\n");
        prompt.append(question);

        return prompt.toString();
    }

    /** Instruction bổ sung dựa trên loại câu hỏi. */
    private String buildQueryTypeInstruction(String queryTypeHint) {
        return switch (queryTypeHint) {
            case "COUNT_QUERY" -> """
                    Đây là câu hỏi ĐẾM. Bắt buộc:
                    1. Duyệt TẤT CẢ Source để tìm đủ danh sách đối tượng.
                    2. Deduplicate danh sách.
                    3. Đếm số lượng và liệt kê đầy đủ kèm theo.
                    4. Nếu phạm vi mơ hồ, trả lời theo cả hai cách hiểu.
                    """;
            case "LIST_ALL" -> """
                    Đây là câu hỏi LIỆT KÊ ĐẦY ĐỦ. Bắt buộc:
                    1. Duyệt TẤT CẢ Source (đặc biệt section_summary) để tìm đủ items.
                    2. KHÔNG bỏ sót items ở các Source cuối.
                    3. Giữ nguyên nhóm logic trong tài liệu.
                    4. Nếu có Source là section_summary, đọc trước để có tổng quan.
                    """;
            case "TABLE_LOOKUP" -> """
                    Đây là câu hỏi TRA CỨU BẢNG (TABLE_LOOKUP). Bắt buộc:
                    1. Ưu tiên đọc các Source có Type=table_summary, table_row_group, text_table_like, và mọi Content có cấu trúc bảng Markdown (có ký tự '|' theo hàng/cột rõ ràng).
                    2. Nếu câu hỏi nêu tên bảng cụ thể trong tài liệu, CHỈ dùng Source khớp bảng đó; không lấy cột từ bảng khác.
                    3. Xác định hàng/dòng (đối tượng/entity trong câu hỏi) và cột/thuộc tính (giá trị cần tra). Đọc đúng ô giao của hàng và cột đó.
                    4. Nếu câu hỏi đưa ra nhiều lựa chọn giá trị (ví dụ dạng “… hay …?”), chọn lựa chọn khớp với giá trị trong ô của bảng; trả lời ngắn gọn và căn cứ vào ô/hàng/cột tương ứng — không suy diễn ngoài ô đã đọc.
                    5. Gộp TẤT CẢ dòng từ các Source table_row_group thuộc đúng phạm vi bảng được hỏi thành một bảng Markdown; không bỏ sót dòng thuộc phạm vi.
                    6. KHÔNG được dùng câu “Tôi không tìm thấy thông tin này trong tài liệu.” khi trong [TÀI LIỆU THAM KHẢO] đã có hàng/cột/ô trực tiếp trả lời câu hỏi (kể cả câu dạng lựa chọn). Chỉ dùng câu từ chối đó khi không có hàng/cột/giá trị liên quan trong context.
                    7. Không đoán hoặc bịa giá trị không xuất hiện trong các Source; không suy luận ngoài nội dung ô/hàng đã có.
                    """;
            case "SECTION_SUMMARY" -> """
                    Đây là câu hỏi về NỘI DUNG MỘT SECTION. Bắt buộc:
                    1. Đọc Source có Type=section_summary trước (nếu có).
                    2. Tổng hợp từ TẤT CẢ Source thuộc section và subsection liên quan.
                    3. Giữ cấu trúc phân cấp (heading cha → heading con) trong câu trả lời.
                    4. KHÔNG bỏ sót subsection nào.
                    """;
            case "TABLE_LIKE" -> """
                    Source có Type=text_table_like chứa dữ liệu dạng bảng chưa được parse thành bảng chính thức.
                    BẮT BUỘC:
                    1. Đọc và giữ nguyên quan hệ cột-hàng trong content.
                    2. Nếu nhận ra pattern cột, trình bày bằng bảng Markdown.
                    3. Nếu không chắc pattern cột, trình bày theo "thuộc tính — giá trị" cho từng dòng.
                    4. KHÔNG flatten thành danh sách phẳng.
                    """;
            default -> "";
        };
    }

    /** Legacy method. */
    public String buildUserPrompt(
            String question,
            List<String> contextChunks,
            List<ChatMessage> chatHistory) {

        StringBuilder prompt = new StringBuilder();

        List<String> uniqueChunks = contextChunks.stream()
                .filter(chunk -> chunk != null && !chunk.isBlank())
                .map(String::trim)
                .distinct()
                .limit(6)
                .collect(Collectors.toList());

        prompt.append("[TÀI LIỆU THAM KHẢO]\n");
        for (int i = 0; i < uniqueChunks.size(); i++) {
            prompt.append("Đoạn ").append(i + 1).append(":\n");
            prompt.append(uniqueChunks.get(i)).append("\n\n");
        }

        if (!chatHistory.isEmpty()) {
            prompt.append("[LỊCH SỬ HỘI THOẠI]\n");
            for (ChatMessage msg : chatHistory) {
                String role = "USER".equals(msg.getRole().name().toUpperCase(Locale.ROOT))
                        ? "Người dùng" : "Trợ lý";
                prompt.append(role).append(": ").append(msg.getContent()).append("\n");
            }
            prompt.append("\n");
        }

        prompt.append("[CÂU HỎI HIỆN TẠI]\n");
        prompt.append(question);

        return prompt.toString();
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }

    /**
     * True nếu có chunk loại bảng hoặc nội dung giống bảng Markdown (không gọi DB/Qdrant).
     */
    private boolean contextsContainNormalizedTableRow(List<RetrievedContext> contexts) {
        if (contexts == null || contexts.isEmpty()) {
            return false;
        }
        return contexts.stream()
                .anyMatch(c -> c != null && "normalized_table_row".equalsIgnoreCase(
                        nullSafe(c.getChunkType()).trim()));
    }

    private boolean contextsContainTableLikeChunks(List<RetrievedContext> contexts) {
        if (contexts == null || contexts.isEmpty()) {
            return false;
        }
        for (RetrievedContext c : contexts) {
            if (c == null) {
                continue;
            }
            String chunkType = c.getChunkType();
            if (chunkType != null) {
                String t = chunkType.trim().toLowerCase(Locale.ROOT);
                if ("table_summary".equals(t) || "normalized_table_row".equals(t)
                        || "table_row_group".equals(t) || "text_table_like".equals(t)) {
                    return true;
                }
            }
            if (contentLooksLikeMarkdownTable(c.getContent())) {
                return true;
            }
        }
        return false;
    }

    private static boolean contentLooksLikeMarkdownTable(String content) {
        if (content == null || content.isBlank()) {
            return false;
        }
        return content.lines().anyMatch(line -> {
            if (line == null || line.isBlank()) {
                return false;
            }
            long pipes = line.chars().filter(ch -> ch == '|').count();
            return pipes >= 2;
        });
    }
}
