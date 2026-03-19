package KLTN.RAG_CHATBOT_BE.service;

import KLTN.RAG_CHATBOT_BE.domain.chat.ChatMessage;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class PromptBuilderService {

    // System prompt định nghĩa vai trò chatbot
    private static final String SYSTEM_PROMPT = """
            Bạn là trợ lý AI hỗ trợ trả lời câu hỏi dựa trên tài liệu được cung cấp.

            Nguyên tắc trả lời:
            1. Chỉ trả lời dựa trên nội dung trong phần [TÀI LIỆU THAM KHẢO] bên dưới.
            2. Nếu câu hỏi không liên quan đến tài liệu, hãy nói: "Tôi không tìm thấy thông tin này trong tài liệu."
            3. Trả lời bằng tiếng Việt, rõ ràng và ngắn gọn.
            4. Không bịa đặt thông tin ngoài tài liệu.
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