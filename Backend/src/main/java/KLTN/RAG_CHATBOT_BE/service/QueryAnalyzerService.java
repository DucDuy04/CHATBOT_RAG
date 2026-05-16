package KLTN.RAG_CHATBOT_BE.service;

import KLTN.RAG_CHATBOT_BE.domain.document.DocumentSection;
import KLTN.RAG_CHATBOT_BE.domain.document.DocumentSectionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class QueryAnalyzerService {

    public enum QueryType {
        /** Câu hỏi thực thể đơn giản. */
        NORMAL_FACT,
        /** Cần liệt kê đầy đủ toàn bộ items. */
        LIST_ALL,
        /** Cần tra cứu bảng biểu. */
        TABLE_LOOKUP,
        /** Hỏi về nội dung / tóm tắt một section. */
        SECTION_SUMMARY,
        /** Câu hỏi đếm: "bao nhiêu", "có mấy". */
        COUNT_QUERY,
        /** Section trải dài nhiều trang. */
        CROSS_PAGE_SECTION
    }

    /**
     * Kết quả match giữa câu hỏi và một section trong tài liệu.
     *
     * Scoring:
     *   titleHits   — số term của query match trực tiếp trong title (trọng số cao nhất)
     *   totalScore  — điểm tổng hợp (xem buildScore để biết công thức)
     *
     * @param sectionKey  Khóa định danh section, ví dụ "sec_6.2"
     * @param title       Tiêu đề gốc (không normalize)
     * @param headingPath Đường dẫn heading đầy đủ, ví dụ "6 Parent > 6.2 Child"
     * @param titleHits   Số term của query khớp trong title
     * @param totalScore  Điểm scoring cuối (có thể âm nếu bị phạt nặng)
     */
    public record HeadingMatch(
            String sectionKey,
            String title,
            String headingPath,
            int titleHits,
            int totalScore
    ) {}

    private final DocumentSectionRepository documentSectionRepository;

    // ===================================================================
    // QUERY TYPE ANALYSIS
    // ===================================================================

    public QueryType analyze(String question, UUID widgetId) {
        if (question == null || question.isBlank()) {
            return QueryType.NORMAL_FACT;
        }

        String q = normalize(question);

        // True item-count questions must stay COUNT_QUERY even when they mention "bảng"
        // or contain "bao nhiêu" (collision with table cell "giá là bao nhiêu").
        if (isExplicitItemCountQuery(q)) {
            return QueryType.COUNT_QUERY;
        }

        // Table cell / row attribute lookup (scalar in a row / cell), often with "bao nhiêu",
        // must not be classified as COUNT_QUERY. Uses structural cues only — no hardcoded entity names.
        if (isTableCellLookupQuery(q)) {
            return QueryType.TABLE_LOOKUP;
        }

        if (containsAny(q,
                "bao nhieu", "co may", "may cai", "may loai", "may buoc",
                "tong so", "so luong", "dem tat ca", "co bao nhieu",
                "how many", "count")) {
            return QueryType.COUNT_QUERY;
        }

        if (containsAny(q,
                "liet ke", "tat ca", "toan bo", "danh sach", "day du",
                "bao gom", "gom nhung gi", "gom gi", "gom co", "gom",
                "nhung gi", "nhung loai", "nhung thanh phan",
                "trinh bay", "tom tat", "tong hop",
                "bao gom nhung gi", "listat", "list all"
               )) {
            return QueryType.LIST_ALL;
        }

        if (containsAny(q,
                "bang", "cot", " row", "column", "table",
                " ma ", " ma,", " ma.", "sku", "id", "code", "gia tri", "so lieu",
                "thong ke", "chi tiet bang", "tra bang")
                || TABLE_ROW_HANG.matcher(q).find()) {
            return QueryType.TABLE_LOOKUP;
        }

        if (q.matches(".*\\b\\d+(\\.\\d+)+\\b.*") || containsAny(q,
                "muc", "phan", "chuong", "section", "noi ve", "trinh bay ve",
                "mo ta", "giai thich", "nen tang", "kien truc", "architecture",
                "quy trinh", "workflow", "process",
                "yeu cau", "requirement",
                "muc tieu", "objective", "goal",
                "chuc nang", "function", "feature",
                "pham vi", "scope",
                "so sanh", "compare",
                "thiet ke", "design",
                "tong quan", "overview",
                "cac buoc", "step", "cac giai doan", "phase")) {
            return QueryType.SECTION_SUMMARY;
        }

        if (widgetId != null && isLikelyHeadingQueryByWidget(q, widgetId)) {
            return QueryType.SECTION_SUMMARY;
        }

        return QueryType.NORMAL_FACT;
    }

    public QueryType analyze(String question) {
        return analyze(question, null);
    }

    // ===================================================================
    // HEADING MATCH  —  primary entry point (fetches sections from DB)
    // ===================================================================

    /**
     * Tìm các section có title/heading path khớp tốt nhất với câu hỏi.
     * Tự động fetch danh sách section từ DB.
     *
     * @return Danh sách match, sắp xếp GIẢM DẦN theo totalScore (tốt nhất ở vị trí 0).
     */
    public List<HeadingMatch> findMatchedSections(String question, UUID widgetId) {
        if (question == null || question.isBlank() || widgetId == null) return List.of();

        List<DocumentSection> sections =
                documentSectionRepository.findByWidgetConfigIdOrderByOrderIndexAsc(widgetId);

        return findMatchedSections(question, sections);
    }

    /**
     * Tìm các section có title/heading path khớp tốt nhất với câu hỏi.
     * Nhận danh sách section đã fetch sẵn (tránh DB round-trip thừa).
     *
     * ── Scoring formula ──────────────────────────────────────────────────────
     * base      = titleHits × 3  +  pathOnlyHits × 1
     * penalty   = missedTerms   × 2   (terms not found in title OR path)
     * bonus1    = significantTerms.size() × 2   (nếu title chứa toàn bộ query phrase)
     * bonus2    = significantTerms.size()        (nếu titleHits == significantTerms.size(),
     *                                             tức mọi term đều khớp trong title)
     * totalScore = base − penalty + bonus1 + bonus2
     *
     * ── Qualification threshold ──────────────────────────────────────────────
     * Được đưa vào kết quả nếu:
     *   (titleHits >= 2 OR (titleHits >= 1 AND pathOnlyHits >= 1)) AND totalScore > 0
     *
     * ── Sort order ───────────────────────────────────────────────────────────
     * 1. totalScore DESC        (điểm càng cao, match càng tốt)
     * 2. titleHits   DESC       (tiebreaker: nhiều term match trong title hơn)
     * 3. section depth DESC     (tiebreaker: section con cụ thể hơn section cha)
     *
     * @return Danh sách tối đa 5 match, phần tử index-0 là match TỐT NHẤT.
     */
    public List<HeadingMatch> findMatchedSections(String question, List<DocumentSection> sections) {
        if (question == null || question.isBlank()
                || sections == null || sections.isEmpty()) return List.of();

        String q = normalize(question);
        String[] qTerms = q.split("[^\\p{L}\\p{N}]+");

        List<String> significantTerms = Arrays.stream(qTerms)
                .filter(t -> t.length() >= 3)
                .distinct()
                .toList();

        if (significantTerms.isEmpty()) {
            log.debug("[QA] findMatchedSections: no significant terms in query='{}'", question);
            return List.of();
        }

        int totalTerms = significantTerms.size();
        log.debug("[QA] findMatchedSections: query='{}' terms={} scanning {} sections",
                question, significantTerms, sections.size());

        List<HeadingMatch> matches = new ArrayList<>();

        for (DocumentSection sec : sections) {
            String title = normalize(sec.getTitle());
            String path  = normalize(safeStr(sec.getHeadingPathText()));

            int titleHits    = 0;
            int pathOnlyHits = 0;

            for (String term : significantTerms) {
                boolean inTitle = !title.isBlank() && title.contains(term);
                boolean inPath  = !path.isBlank()  && path.contains(term);

                if (inTitle) {
                    titleHits++;
                } else if (inPath) {
                    pathOnlyHits++;
                }
            }

            int missedTerms = totalTerms - titleHits - pathOnlyHits;

            // Base score: title match = 3pt, path-only = 1pt
            int score = titleHits * 3 + pathOnlyHits;

            // Penalty: each term not found anywhere → -2pt
            score -= missedTerms * 2;

            // Bonus-1: query phrase is fully contained in the section title
            // (title must be long enough to hold the full query → strong exact-match signal)
            if (!title.isBlank() && !q.isBlank() && title.contains(q.trim())) {
                score += totalTerms * 2;
            }

            // Bonus-2: ALL query terms found directly in title (100% title coverage)
            if (titleHits == totalTerms && totalTerms >= 2) {
                score += totalTerms;
            }

            // Qualification: must have enough title signal AND positive net score
            boolean qualifies = (titleHits >= 2
                    || (titleHits >= 1 && pathOnlyHits >= 1 && totalTerms >= 2))
                    && score > 0;

            if (qualifies) {
                log.debug("[QA] Section '{}' [{}]: titleHits={} pathHits={} missed={} score={}",
                        sec.getTitle(), sec.getSectionKey(), titleHits, pathOnlyHits, missedTerms, score);
                matches.add(new HeadingMatch(
                        sec.getSectionKey(), sec.getTitle(), sec.getHeadingPathText(),
                        titleHits, score));
            }
        }

        // ── Sort: DESCENDING score, then titleHits, then section depth (more specific = deeper) ──
        // NOTE: use explicit lambda — do NOT chain .reversed() twice, as Java reverses the
        //       ENTIRE composed comparator, not just the last thenComparing clause.
        List<HeadingMatch> top = matches.stream()
                .sorted((a, b) -> {
                    // Primary: higher totalScore first
                    int cmp = Integer.compare(b.totalScore(), a.totalScore());
                    if (cmp != 0) return cmp;

                    // Secondary: more title hits first
                    cmp = Integer.compare(b.titleHits(), a.titleHits());
                    if (cmp != 0) return cmp;

                    // Tertiary: deeper (more specific) section first
                    // e.g. "sec_6.2.1" (depth 3) beats "sec_6.2" (depth 2) on a tie
                    int aDepth = a.sectionKey().split("\\.").length;
                    int bDepth = b.sectionKey().split("\\.").length;
                    return Integer.compare(bDepth, aDepth);
                })
                .limit(5)
                .toList();

        if (!top.isEmpty()) {
            log.info("[QA] Heading match candidates (top {}, best-first): {}", top.size(),
                    top.stream().map(m ->
                            "'" + m.title() + "' [" + m.sectionKey()
                            + " titleHits=" + m.titleHits()
                            + " score=" + m.totalScore() + "]").toList());
        } else {
            log.debug("[QA] No heading match candidates (terms={} allSections={})",
                    significantTerms, sections.size());
        }

        return top;
    }

    // ===================================================================
    // QUERY REWRITING
    // ===================================================================

    public List<String> rewriteQuery(String question) {
        List<String> variants = new ArrayList<>();
        if (question == null || question.isBlank()) return variants;

        String trimmed = question.trim();
        variants.add(trimmed);

        String stripped = trimmed
                .replaceAll("(?i)^(hãy|bạn hãy|vui lòng|cho tôi biết|cho biết|hỏi về)\\s+", "")
                .replaceAll("(?i)^(what is|what are|tell me about|describe|explain|list)\\s+", "")
                .replaceAll("[?？]$", "")
                .trim();
        if (!stripped.equalsIgnoreCase(trimmed) && !stripped.isBlank()) {
            variants.add(stripped);
        }

        String core = stripped
                .replaceAll("(?i)\\s+(gồm những gì|là gì|có gì|như thế nào|là như thế nào|bao gồm gì|bao gồm những gì)\\s*$", "")
                .trim();
        if (!core.equalsIgnoreCase(stripped) && !core.isBlank()) {
            variants.add(core);
        }

        String normalized = normalize(trimmed);
        if (!normalized.equalsIgnoreCase(trimmed) && !normalized.isBlank()) {
            variants.add(normalized);
        }

        return variants.stream().distinct().toList();
    }

    // ===================================================================
    // PRIVATE HELPERS
    // ===================================================================

    private static final Pattern SPECIFIC_ROW_CATEGORY_ENTITY = Pattern.compile(
            "(^|\\s)(goi|san pham|dich vu|sku|\\bma\\b|\\bdong\\b|\\bhang\\b|\\bcot\\b|nhan vien|khach hang)\\s+"
                    + "([\\p{L}\\p{N}][\\p{L}\\p{N}_\\-\\.]*(?:\\s+[\\p{L}\\p{N}][\\p{L}\\p{N}_\\-\\.]*){0,4})",
            Pattern.UNICODE_CHARACTER_CLASS);

    /** SKU-123 / SKU 456 (no space after SKU still counts as a concrete code reference). */
    private static final Pattern SPECIFIC_SKU_REFERENCE = Pattern.compile(
            "\\bsku[-\\s]?[\\p{L}\\p{N}_\\-\\.]+",
            Pattern.UNICODE_CHARACTER_CLASS);

    /**
     * "..., Entity có giá/ton kho/..." — entity token is not interpreted; only the "có &lt;field&gt;" frame.
     */
    private static final Pattern ENTITY_THEN_CO_FIELD = Pattern.compile(
            "(?:^|[,\\s]+)([\\p{L}\\p{N}][\\p{L}\\p{N}_\\-\\.]*(?:\\s+[\\p{L}\\p{N}][\\p{L}\\p{N}_\\-\\.]*){0,3})\\s+co\\s+"
                    + "(gia|ton kho|luot|trang thai|phong ban|thuoc|kenh|ho tro|muc|ngay|han|gia tri|bao nhieu\\s+vnd)\\b",
            Pattern.UNICODE_CHARACTER_CLASS);

    private boolean isLikelyHeadingQueryByWidget(String normalizedQuestion, UUID widgetId) {
        List<DocumentSection> sections = documentSectionRepository
                .findTop200ByWidgetConfigIdOrderByOrderIndexAsc(widgetId);
        if (sections == null || sections.isEmpty()) return false;

        String[] qTerms = normalizedQuestion.split("[^\\p{L}\\p{N}]+");
        int goodTerms = 0;
        for (String t : qTerms) {
            if (t.length() >= 4) goodTerms++;
        }
        if (goodTerms == 0) return false;

        for (DocumentSection sec : sections) {
            String title = normalize(sec.getTitle());
            String path  = normalize(safeStr(sec.getHeadingPathText()));
            int hit = 0;
            for (String t : qTerms) {
                if (t.length() < 4) continue;
                if ((!title.isBlank() && title.contains(t))
                        || (!path.isBlank() && path.contains(t))) {
                    hit++;
                }
            }
            if (hit >= 2) return true;
        }
        return false;
    }

    private boolean containsAny(String text, String... keywords) {
        for (String kw : keywords) {
            if (text.contains(kw)) return true;
        }
        return false;
    }

    /**
     * Counting rows/items (how many packages / policies / rows in a table), including phrasing
     * inside a table context. Runs before {@link #isTableCellLookupQuery} to avoid collision.
     * Uses category words only — no product/company proper names.
     */
    private boolean isExplicitItemCountQuery(String q) {
        if (q.isBlank()) return false;

        if (containsAny(q, " dem ", " dem.", "dem so", "dem tat ca")
                || q.startsWith("dem ")
                || q.endsWith(" dem")) {
            return true;
        }

        if (q.contains("so luong") && hasCountTargetCategory(q)) {
            return true;
        }

        if (containsAny(q,
                "co bao nhieu goi", "bao nhieu goi dich vu", "bao nhieu hang goi",
                "bao nhieu hang", "co bao nhieu chinh sach", "bao nhieu chinh sach",
                "co bao nhieu san pham", "bao nhieu san pham trong bang", "trong bang co bao nhieu san pham",
                "co bao nhieu nhan vien", "bao nhieu nhan vien trong danh sach",
                "co bao nhieu dich vu", "dem so dich vu", "dem dich vu",
                "co bao nhieu dong", "co bao nhieu dong trong bang", "bao nhieu dong trong bang", "bang nay co bao nhieu dong",
                "co bao nhieu muc", "bao nhieu muc ", "co bao nhieu khach hang",
                "co bao nhieu ban ghi", "co bao nhieu record", "co bao nhieu item",
                "trong danh sach co bao nhieu")) {
            return true;
        }

        int idx = q.indexOf("bao nhieu goi");
        if (idx >= 0) {
            char before = idx == 0 ? ' ' : q.charAt(idx - 1);
            if (before == ' ' || before == '?' || before == ',' || idx == 0) {
                return true;
            }
        }

        return false;
    }

    /** Nouns / targets for aggregate counts (category words only — avoid "hang" inside "thang", "dong" in "duong"). */
    private boolean hasCountTargetCategory(String q) {
        if (containsAny(q,
                "goi", "san pham", "chinh sach", "muc", "nhan vien", "dich vu",
                "khach hang", "ban ghi", "record", "row", "item", "du lieu", "danh sach", "truong")) {
            return true;
        }
        if (TABLE_ROW_HANG.matcher(q).find()) {
            return true;
        }
        return containsAny(q, "dong du lieu", " bao nhieu dong", "co bao nhieu dong", " so dong");
    }

    /**
     * Table / spreadsheet context (section cue), without matching "hang" inside "thang" (month).
     */
    private boolean hasTableCue(String q) {
        if (q.isBlank()) return false;
        if (containsAny(q, "trong bang", "theo bang", "bang gia", "bang goi", "trong danh sach")) {
            return true;
        }
        if (q.startsWith("bang ") || containsAny(q, " bang ", " bang.", " bang,", " bang?")) {
            return true;
        }
        if (q.startsWith("dong ") || containsAny(q, " dong ", " dong,", " dong.", " dong?")) {
            return true;
        }
        if (TABLE_ROW_HANG.matcher(q).find()) {
            return true;
        }
        return containsAny(q, " cot ", " cot,", " o ", " o.", " o,", " o?",
                " row", "column", " cell", " table");
    }

    private static final Pattern TABLE_ROW_HANG = Pattern.compile("(^|\\s)hang(\\s|,|\\.|\\?|$)");

    /**
     * Asks for a scalar cell / attribute (price, quota, channel, "là gì", column value), not "how many items".
     */
    private boolean hasValueFieldCue(String q) {
        if (q.isBlank()) return false;
        if (containsAny(q,
                " gia ", " gia,", " gia.", " gia?", "gia la", "gia goi", "gia san pham",
                "gia dich vu", "co gia ", " co gia", "co gia?",
                "vnd", "vnđ",
                "luot hoi", " luot ", "luot ai", "luot su dung", "su dung moi thang",
                "ton kho", "so luong con lai",
                " ho tro", "hotro", "kenh", "uu tien",
                "trang thai", "phong ban", " thuoc ", "thuoc phong",
                " la gi", " la gi?", "gia tri cot", " cot ", " cot ho tro", "gia tri",
                " muc ", " ngay ", " han ", " nao trong bang")) {
            return true;
        }
        if (q.startsWith("gia ") || q.endsWith(" gia") || q.contains(" gia la ")) {
            return true;
        }
        return false;
    }

    /**
     * Concrete row / entity reference by linguistic frame (category word + tail, SKU, "X có giá", dòng X).
     * Does not inspect the tail token — no hardcoded product or company names.
     */
    private boolean hasSpecificRowReference(String q) {
        if (q.isBlank()) return false;

        if (SPECIFIC_SKU_REFERENCE.matcher(q).find()) {
            return true;
        }

        var m = SPECIFIC_ROW_CATEGORY_ENTITY.matcher(q);
        while (m.find()) {
            String category = m.group(2);
            String tail = m.group(3) != null ? m.group(3).trim() : "";
            if (tail.length() < 2) {
                continue;
            }
            if (isGenericCategoryTail(category, tail)) {
                continue;
            }
            return true;
        }

        return ENTITY_THEN_CO_FIELD.matcher(q).find();
    }

    /** Tails that mean "type of offering" rather than a named plan row in count questions. */
    private boolean isGenericCategoryTail(String category, String tail) {
        if (!"goi".equals(category)) {
            return false;
        }
        String t = tail.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
        return t.equals("dich") || t.equals("vu") || t.equals("dich vu");
    }

    /**
     * Reads a single cell / attribute from a table row, including "bao nhiêu" as a scalar ask.
     */
    private boolean isTableCellLookupQuery(String q) {
        if (q.isBlank()) return false;

        boolean tableCue = hasTableCue(q);
        boolean valueCue = hasValueFieldCue(q);
        boolean specificRow = hasSpecificRowReference(q);

        if (tableCue && valueCue) {
            return true;
        }
        return specificRow && valueCue;
    }

    public String normalize(String value) {
        if (value == null) return "";
        String n = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace('đ', 'd')
                .replace('Đ', 'D')
                .toLowerCase(Locale.ROOT);
        return n.replaceAll("\\s+", " ").trim();
    }

    private String safeStr(String v) {
        return v == null ? "" : v;
    }
}
