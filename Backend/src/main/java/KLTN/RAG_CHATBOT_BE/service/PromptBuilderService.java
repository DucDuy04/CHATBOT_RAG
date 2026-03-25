package KLTN.RAG_CHATBOT_BE.service;

import KLTN.RAG_CHATBOT_BE.domain.chat.ChatMessage;
import org.springframework.stereotype.Service;
import java.util.List;

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
            Bạn là trợ lý AI thông minh, hỗ trợ trả lời câu hỏi dựa trên tài liệu được cung cấp.

            Nguyên tắc trả lời:
            1. Ưu tiên sử dụng nội dung trong [TÀI LIỆU THAM KHẢO] làm cơ sở trả lời.
            2. Nếu câu hỏi yêu cầu giải thích, phân tích hoặc mở rộng — hãy dùng kiến thức
               của bạn để giải thích rõ hơn, miễn là không mâu thuẫn với tài liệu.
            3. Nếu câu hỏi hoàn toàn không liên quan đến tài liệu và bạn không có đủ
               thông tin, hãy nói: "Tôi không tìm thấy thông tin này trong tài liệu."
            4. Trả lời bằng tiếng Việt, rõ ràng, đầy đủ và có cấu trúc.
            5. Với câu hỏi yêu cầu liệt kê hoặc giải thích nhiều mục — trình bày
               từng mục rõ ràng, có thể dùng danh sách hoặc đoạn văn tùy ngữ cảnh.
            6. Không bịa đặt thông tin hoàn toàn ngoài tài liệu.
            """;

    public String buildPrompt(
            String question,
            List<String> contextChunks,
            List<ChatMessage> chatHistory) {

        StringBuilder prompt = new StringBuilder();

        // 1. System prompt
        prompt.append(SYSTEM_PROMPT).append("\n\n");

        // 2. Context từ Qdrant
        prompt.append("[TÀI LIỆU THAM KHẢO]\n");
        for (int i = 0; i < contextChunks.size(); i++) {
            prompt.append("Đoạn ").append(i + 1).append(":\n");
            prompt.append(contextChunks.get(i)).append("\n\n");
        }

        // 3. Lịch sử chat (nếu có)
        if (!chatHistory.isEmpty()) {
            prompt.append("[LỊCH SỬ HỘI THOẠI]\n");
            for (ChatMessage msg : chatHistory) {
                String role = msg.getRole() == ChatMessage.MessageRole.USER
                        ? "Người dùng"
                        : "Trợ lý";
                prompt.append(role).append(": ").append(msg.getContent()).append("\n");
            }
            prompt.append("\n");
        }

        // 4. Câu hỏi hiện tại
        prompt.append("[CÂU HỎI HIỆN TẠI]\n");
        prompt.append(question);

        return prompt.toString();
    }
}