package KLTN.RAG_CHATBOT_BE.service;

import KLTN.RAG_CHATBOT_BE.domain.document.DocumentSection;
import KLTN.RAG_CHATBOT_BE.domain.document.DocumentSectionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class QueryAnalyzerService {

    public enum QueryType {
        NORMAL_FACT,
        LIST_ALL,
        TABLE_LOOKUP,
        SECTION_SUMMARY,
        CROSS_PAGE_SECTION
    }

    private final DocumentSectionRepository documentSectionRepository;

    public QueryType analyze(String question, UUID widgetId) {
        if (question == null || question.isBlank()) {
            return QueryType.NORMAL_FACT;
        }

        String q = normalize(question);

        // Query dạng liệt kê/bao gồm/trình bày toàn bộ → cần mở rộng context để tránh bỏ sót
        if (containsAny(q,
                "liet ke", "tat ca", "toan bo", "danh sach", "day du",
                "bao gom", "gom", "trinh bay", "tom tat", "tong hop"
        )) {
            return QueryType.LIST_ALL;
        }

        // Query về bảng/hàng/cột/mã → ưu tiên TABLE_LOOKUP (để retrieval lấy đủ table chunks)
        if (containsAny(q,
                "bang", "cot", "hang", "row", "column",
                "ma", "sku", "id", "code"
        )) {
            return QueryType.TABLE_LOOKUP;
        }

        // Tổng quát: nếu câu hỏi nhắc đến số mục (vd 6.2, 10.4) hoặc các từ chỉ cấu trúc → SECTION_SUMMARY
        if (q.matches(".*\\b\\d+(\\.\\d+)+\\b.*") || containsAny(q, "muc", "phan", "chuong", "section")) {
            return QueryType.SECTION_SUMMARY;
        }

        // Tổng quát theo NHIỀU file: thử match câu hỏi với danh sách heading/title đã ingest (theo widgetId).
        // Nếu match được heading thì coi như SECTION_SUMMARY để retrieval mở rộng context đúng section.
        if (widgetId != null && isLikelyHeadingQueryByWidget(q, widgetId)) {
            return QueryType.SECTION_SUMMARY;
        }

        return QueryType.NORMAL_FACT;
    }

    // Backward compatibility (nếu chỗ nào đó chưa truyền widgetId)
    public QueryType analyze(String question) {
        return analyze(question, null);
    }

    private boolean isLikelyHeadingQueryByWidget(String normalizedQuestion, UUID widgetId) {
        // Giới hạn top N để tránh query quá nặng; đủ tốt vì orderIndex giữ cấu trúc chính.
        List<DocumentSection> sections = documentSectionRepository
                .findTop200ByWidgetConfigIdOrderByOrderIndexAsc(widgetId);
        if (sections == null || sections.isEmpty()) {
            return false;
        }

        // Lấy các term quan trọng từ câu hỏi để so contains với title/heading_path (đã normalize).
        String[] qTerms = normalizedQuestion.split("[^\\p{L}\\p{N}]+");
        int goodTerms = 0;
        for (String t : qTerms) {
            if (t.length() >= 4) {
                goodTerms++;
            }
        }
        if (goodTerms == 0) return false;

        for (DocumentSection sec : sections) {
            String title = normalize(sec.getTitle());
            String path = normalize(sec.getHeadingPathText());
            // Nếu câu hỏi "đụng" đủ nhiều term với title/path thì coi là hỏi theo heading
            int hit = 0;
            for (String t : qTerms) {
                if (t.length() < 4) continue;
                if ((!title.isBlank() && title.contains(t)) || (!path.isBlank() && path.contains(t))) {
                    hit++;
                }
            }
            if (hit >= 2) {
                return true;
            }
        }

        return false;
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace('đ', 'd')
                .replace('Đ', 'D')
                .toLowerCase(Locale.ROOT);

        return normalized.replaceAll("\\s+", " ").trim();
    }
}
