package KLTN.RAG_CHATBOT_BE.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import KLTN.RAG_CHATBOT_BE.record.DocumentChunk;
import KLTN.RAG_CHATBOT_BE.record.Section;

@Service
public class ChunkingService2 {

    private static final int CHUNK_SIZE = 1200;
    private static final int CHUNK_OVERLAP = 200;

    /**
     * HÀM CHÍNH: Nhận List<Section> từ DocumentParserService và xuất ra List<DocumentChunk>
     */
    public List<DocumentChunk> processSections2(List<Section> sections) {
        List<DocumentChunk> finalChunks = new ArrayList<>();

        if (sections == null || sections.isEmpty()) {
            return finalChunks;
        }

        for (Section section : sections) {
            String fullText = section.content();
            
            // 1. Tách Table và Text thường
            List<String> segments = splitByTableBlocks(fullText);

            for (String segment : segments) {
                if (segment.startsWith("[TABLE_START]")) {
                    // XỬ LÝ TABLE: Giữ nguyên toàn bộ bảng, không cắt gọt để tránh hỏng cấu trúc Markdown
                    String tableContent = segment
                            .replace("[TABLE_START]", "")
                            .replace("[TABLE_END]", "")
                            .trim();
                            
                    if (!tableContent.isBlank()) {
                        DocumentChunk chunk = createEnrichedChunk(tableContent, section);
                        finalChunks.add(chunk);
                    }
                } else {
                    // XỬ LÝ TEXT THƯỜNG: Băm nhỏ theo logic Overlap
                    List<String> textChunks = chunkPlainText(segment);
                    
                    for (String textChunk : textChunks) {
                        DocumentChunk chunk = createEnrichedChunk(textChunk, section);
                        finalChunks.add(chunk);
                    }
                }
            }
        }

        return finalChunks;
    }

    /**
     * ĐÂY LÀ "VŨ KHÍ BÍ MẬT" CỦA RAG: Header Injection
     * Trộn Header vào đầu chuỗi content để VectorDB bắt ngữ cảnh tốt hơn.
     */
    private DocumentChunk createEnrichedChunk(String rawContent, Section section) {
        // Ví dụ: "Ngữ cảnh: [2.1 Data Collection]\nNội dung text..."
        String enrichedContent = "Ngữ cảnh: [" + section.header() + "]\n" + rawContent;
        
        return new DocumentChunk(
            enrichedContent,
            section.header(),
            section.startPage(),
            section.endPage()
        );
    }

    // ==========================================
    // LOGIC CŨ CỦA BẠN (GIỮ NGUYÊN VÌ RẤT TỐT)
    // ==========================================

    private List<String> splitByTableBlocks(String text) {
        List<String> segments = new ArrayList<>();
        int searchFrom = 0;

        while (searchFrom < text.length()) {
            int tableStart = text.indexOf("[TABLE_START]", searchFrom);

            if (tableStart == -1) {
                String remaining = text.substring(searchFrom).trim();
                if (!remaining.isBlank()) segments.add(remaining);
                break;
            }

            String beforeTable = text.substring(searchFrom, tableStart).trim();
            if (!beforeTable.isBlank()) segments.add(beforeTable);

            int tableEnd = text.indexOf("[TABLE_END]", tableStart);
            if (tableEnd == -1) {
                segments.add(text.substring(tableStart).trim());
                break;
            }

            String tableBlock = text.substring(tableStart, tableEnd + "[TABLE_END]".length());
            segments.add(tableBlock);
            searchFrom = tableEnd + "[TABLE_END]".length();
        }

        return segments;
    }

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
            if (!chunk.isBlank()) chunks.add(chunk);
            if (end >= textLength) break;

            int nextStart = end - CHUNK_OVERLAP;
            start = (nextStart > start) ? nextStart : end;
        }

        return chunks;
    }
}

// record DocumentChunk(
//     String content,     // Nội dung đã được nhúng thêm Header
//     String header,      // Metadata: Mục lục
//     int startPage,      // Metadata: Trang bắt đầu
//     int endPage         // Metadata: Trang kết thúc
// ) {}