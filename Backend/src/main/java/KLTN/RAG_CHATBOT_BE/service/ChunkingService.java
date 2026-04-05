package KLTN.RAG_CHATBOT_BE.service;

import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;

@Service
public class ChunkingService {

    private static final int CHUNK_SIZE = 1200;
    private static final int CHUNK_OVERLAP = 200;

    public List<String> chunk(String text) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isBlank())
            return chunks;

        // THÊM MỚI: Tách riêng text thường và table block trước khi chunk
        List<String> segments = splitByTableBlocks(text);

        for (String segment : segments) {
            if (segment.startsWith("[TABLE_START]")) {
                // Table block → giữ nguyên, không cắt
                String tableContent = segment
                        .replace("[TABLE_START]", "")
                        .replace("[TABLE_END]", "")
                        .trim();
                if (!tableContent.isBlank()) {
                    chunks.add(tableContent);
                }
            } else {
                // Text thường → chunk bình thường như cũ
                chunks.addAll(chunkPlainText(segment));
            }
        }

        return chunks;
    }

    // THÊM MỚI: Tách text thành các segment (text thường xen kẽ table block)
    private List<String> splitByTableBlocks(String text) {
        List<String> segments = new ArrayList<>();
        int searchFrom = 0;

        while (searchFrom < text.length()) {
            int tableStart = text.indexOf("[TABLE_START]", searchFrom);

            if (tableStart == -1) {
                // Không còn table nào → phần còn lại là text thường
                String remaining = text.substring(searchFrom).trim();
                if (!remaining.isBlank())
                    segments.add(remaining);
                break;
            }

            // Phần text thường trước table
            String beforeTable = text.substring(searchFrom, tableStart).trim();
            if (!beforeTable.isBlank())
                segments.add(beforeTable);

            // Lấy toàn bộ table block (kể cả marker)
            int tableEnd = text.indexOf("[TABLE_END]", tableStart);
            if (tableEnd == -1) {
                // Không tìm thấy [TABLE_END] → lấy hết phần còn lại
                segments.add(text.substring(tableStart).trim());
                break;
            }

            String tableBlock = text.substring(tableStart, tableEnd + "[TABLE_END]".length());
            segments.add(tableBlock);
            searchFrom = tableEnd + "[TABLE_END]".length();
        }

        return segments;
    }

    // Logic chunk text thường — giữ nguyên như code gốc của bạn
    private List<String> chunkPlainText(String text) {
        List<String> chunks = new ArrayList<>();
        int start = 0;
        int textLength = text.length();

        while (start < textLength) {
            int end = Math.min(start + CHUNK_SIZE, textLength);

            if (end < textLength) {
                int searchFrom = start + CHUNK_SIZE / 2;
                int lastPeriod = text.lastIndexOf(". ", end);
                int lastNewline = text.lastIndexOf("\n", end);
                int bestBreak = Math.max(lastPeriod, lastNewline);

                if (bestBreak >= searchFrom) {
                    end = bestBreak + 1;
                }
            }

            String chunk = text.substring(start, end).trim();
            if (!chunk.isBlank())
                chunks.add(chunk);
            if (end >= textLength)
                break;

            int nextStart = end - CHUNK_OVERLAP;
            start = (nextStart > start) ? nextStart : end;
        }

        return chunks;
    }
}