package KLTN.RAG_CHATBOT_BE.service;

import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;

@Service
public class ChunkingService {

    private static final int CHUNK_SIZE = 500; // Số ký tự mỗi chunk
    private static final int CHUNK_OVERLAP = 50; // Overlap giữa 2 chunk liền nhau

    public List<String> chunk(String text) {
        List<String> chunks = new ArrayList<>();

        if (text == null || text.isBlank())
            return chunks;

        int start = 0;
        int textLength = text.length();

        while (start < textLength) {
            int end = Math.min(start + CHUNK_SIZE, textLength);

            if (end < textLength) {
                // Chỉ tìm breakpoint trong phạm vi nửa sau của chunk
                int searchFrom = start + CHUNK_SIZE / 2;
                int lastPeriod = text.lastIndexOf(". ", end);
                int lastNewline = text.lastIndexOf("\n", end);
                int bestBreak = Math.max(lastPeriod, lastNewline);

                if (bestBreak >= searchFrom) { // Chỉ dùng nếu trong phạm vi hợp lý
                    end = bestBreak + 1;
                }
            }

            String chunk = text.substring(start, end).trim();
            if (!chunk.isBlank())
                chunks.add(chunk);
            if (end >= textLength)
                break;
            // Đảm bảo start luôn tiến về phía trước
            int nextStart = end - CHUNK_OVERLAP;
            start = (nextStart > start) ? nextStart : end;
        }

        return chunks;
    }
}