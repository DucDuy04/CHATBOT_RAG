package KLTN.RAG_CHATBOT_BE.service;

import KLTN.RAG_CHATBOT_BE.domain.chat.ChatMessage;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
public class PromptBuilderService {

    // System prompt định nghĩa vai trò chatbot
    // private static final String SYSTEM_PROMPT = """
    // Bạn là trợ lý AI hỗ trợ trả lời câu hỏi dựa trên tài liệu được cung cấp.

    // Nguyên tắc trả lời:
    // 1. Chỉ trả lời dựa trên nội dung trong phần [TÀI LIỆU THAM KHẢO] bên dưới.
    // 2. Nếu câu hỏi không liên quan đến tài liệu, hãy nói: "Tôi không tìm thấy
    // thông tin này trong tài liệu."
    // 3. Trả lời bằng tiếng Việt, rõ ràng và ngắn gọn.
    // 4. Không bịa đặt thông tin ngoài tài liệu.
    // """;
    private static final String SYSTEM_PROMPT = """
        Bạn là trợ lý AI chuyên trả lời câu hỏi dựa trên tài liệu được cung cấp.

        NGUYÊN TẮC TRẢ LỜI:
        1. Chỉ sử dụng nội dung trong [TÀI LIỆU THAM KHẢO] để trả lời. KHÔNG dùng kiến thức ngoài tài liệu.
        2. Chỉ trả lời đúng những gì câu hỏi hỏi. KHÔNG thêm thông tin ngoài lề, KHÔNG giải thích thêm khi không được yêu cầu.
        3. Nếu tài liệu không có thông tin, chỉ trả lời đúng 1 câu: "Tôi không tìm thấy thông tin này trong tài liệu."
        4. Trả lời bằng tiếng Việt, ngắn gọn, đúng trọng tâm.

        XỬ LÝ BẢNG VÀ DANH SÁCH:
        5. Khi tài liệu có bảng Markdown (| Cột 1 | Cột 2 |), đọc đúng từng hàng và cột, không nhầm lẫn dữ liệu giữa các hàng.
        6. Nếu câu trả lời chứa nhiều mục hoặc số liệu, trình bày lại dưới dạng bảng Markdown.
        7. Nếu dữ liệu bảng nằm rải rác nhiều chunks, BẮT BUỘC gộp tất cả hàng thành MỘT bảng duy nhất, KHÔNG bỏ sót hàng nào.
        8. Nếu câu hỏi hỏi về danh sách, liệt kê ĐẦY ĐỦ tất cả các mục có trong tài liệu, KHÔNG được bỏ sót.
        """;
        public String getSystemPrompt() {
        return SYSTEM_PROMPT;
    }

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
}