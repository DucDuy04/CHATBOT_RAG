package KLTN.RAG_CHATBOT_BE.service;

import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.Locale;

@Service
public class QueryAnalyzerService {

    public enum QueryType {
        NORMAL_FACT,
        LIST_ALL,
        TABLE_LOOKUP,
        SECTION_SUMMARY,
        CROSS_PAGE_SECTION
    }

    public QueryType analyze(String question) {
        if (question == null || question.isBlank()) {
            return QueryType.NORMAL_FACT;
        }

        String q = normalize(question);

        if (containsAny(q, "liet ke", "tat ca", "toan bo", "danh sach", "day du")) {
            return QueryType.LIST_ALL;
        }

        if (containsAny(q, "bang", "cot", "hang", "ma", "san pham", "row", "column")) {
            return QueryType.TABLE_LOOKUP;
        }

        if (q.matches(".*\\b\\d+(\\.\\d+)+\\b.*") || containsAny(q, "muc", "phan", "chuong", "section")) {
            return QueryType.SECTION_SUMMARY;
        }

        return QueryType.NORMAL_FACT;
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
