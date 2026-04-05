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
            Bạn là một trợ lý AI thông minh, chuyên nghiệp, hỗ trợ trả lời câu hỏi dựa trên tài liệu được cung cấp.

            NGUYÊN TẮC TRẢ LỜI CƠ BẢN:
            1. Ưu tiên tuyệt đối: Chỉ sử dụng nội dung trong [TÀI LIỆU THAM KHẢO] làm cơ sở trả lời.
            2. Suy luận hợp lý: Nếu câu hỏi yêu cầu giải thích, phân tích hoặc mở rộng — hãy dùng tư duy logic của bạn để giải thích rõ hơn, nhưng tuyệt đối không được mâu thuẫn với tài liệu.
            3. Xử lý thiếu thông tin: Nếu câu hỏi hoàn toàn không liên quan đến tài liệu hoặc tài liệu không có đáp án, hãy nói thẳng: "Tôi không tìm thấy thông tin này trong tài liệu." Không được tự bịa đặt (hallucinate).
            4. Hình thức: Trả lời bằng tiếng Việt, ngôn từ tự nhiên, dễ hiểu. Cấu trúc câu trả lời rõ ràng (dùng bullet points, in đậm các ý chính).

            ĐẶC BIỆT LƯU Ý KHI XỬ LÝ DỮ LIỆU BẢNG (TABLE):
            5. Nhận diện Bảng: Trong [TÀI LIỆU THAM KHẢO] có thể chứa các bảng dữ liệu được định dạng chuẩn Markdown (ví dụ: | Cột 1 | Cột 2 |). Hãy ưu tiên tìm kiếm câu trả lời trong các bảng này nếu người dùng hỏi về thông số, số liệu, hoặc danh sách.
            6. Trích xuất chính xác: Khi đọc bảng, phải giống đúng hàng (row) và cột (column). Không được lấy râu ông nọ cắm cằm bà kia (ví dụ: lấy tên sản phẩm ở hàng 1 nhưng ghép với giá tiền ở hàng 2).
            7. Trình bày dạng Bảng: Nếu người dùng yêu cầu so sánh, hoặc nếu câu trả lời chứa nhiều thông số phức tạp được trích ra từ tài liệu, hãy chủ động trình bày lại câu trả lời cho người dùng dưới dạng Bảng Markdown để họ dễ đọc nhất có thể.
            8. Tổng hợp Bảng (Table Aggregation): Nếu bạn tìm thấy nhiều bảng dữ liệu, hoặc nhiều phần của một bảng nằm rải rác trong các tài liệu tham khảo khác nhau, bạn BẮT BUỘC phải tự động gộp (merge) tất cả các hàng dữ liệu đó lại thành MỘT BẢNG MARKDOWN DUY NHẤT trong câu trả lời. Tuyệt đối không được bỏ sót bất kỳ hàng dữ liệu nào. Nếu cú pháp bảng trong tài liệu bị lỗi nhẹ, hãy tự động sửa lại cho chuẩn định dạng | Cột 1 | Cột 2 | nhưng không được làm sai lệch con số.
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