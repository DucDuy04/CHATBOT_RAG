package KLTN.RAG_CHATBOT_BE.service;

import KLTN.RAG_CHATBOT_BE.domain.document.DocumentChunk;
import KLTN.RAG_CHATBOT_BE.domain.document.DocumentChunkRepository;
import KLTN.RAG_CHATBOT_BE.dto.RetrievedContext;
import dev.langchain4j.data.segment.TextSegment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.*;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagRetrievalService {

    private static final int ANCHOR_TOP_K = 30;
    // --- Giới hạn cho query thông thường ---
    private static final int FINAL_LIMIT = 10;              // 12 → 10: tiết kiệm ~2 chunk (~350 token)
    private static final int MAX_CONTEXT_CHARS = 18_000;    // 24k → 18k: tiết kiệm ~1,500 token
    // --- Giới hạn cho query LIST_ALL / TABLE / SECTION_SUMMARY ---
    private static final int FINAL_LIMIT_EXPANDED = 18;     // 25 → 18: tiết kiệm ~1,200 token
    private static final int MAX_CONTEXT_CHARS_EXPANDED = 28_000; // 45k → 28k: tiết kiệm ~4,300 token
    // --- Window mở rộng quanh anchor ---
    private static final int WINDOW_BEFORE = 1;
    private static final int WINDOW_AFTER = 2;
    // --- Giới hạn expansion theo section (chỉ ảnh hưởng pool, không tốn LLM token trực tiếp) ---
    private static final int SECTION_EXPANSION_MAX_CHUNKS = 12;
    private static final int SECTION_EXPANSION_MAX_CHUNKS_EXPANDED = 25; // 30 → 25
    private static final Pattern HEADING_PATTERN =
            Pattern.compile("(?<!\\d)(\\d+(?:\\.\\d+)*)\\.?\\s+([\\p{L}][\\p{L}\\p{N}\\s/&+\\-()]{3,120})");

    private final EmbeddingService embeddingService;
    private final DocumentChunkRepository documentChunkRepository;
    private final QueryAnalyzerService queryAnalyzerService;

    public List<RetrievedContext> retrieve(String question, UUID widgetId) {
        QueryAnalyzerService.QueryType queryType = queryAnalyzerService.analyze(question, widgetId);
        boolean isExpandedQuery = queryType == QueryAnalyzerService.QueryType.LIST_ALL
                || queryType == QueryAnalyzerService.QueryType.TABLE_LOOKUP
                || queryType == QueryAnalyzerService.QueryType.SECTION_SUMMARY;

        log.info("[RAG] question='{}', queryType={}, widgetId={}", question, queryType, widgetId);

        List<TextSegment> anchors;
        try {
            anchors = embeddingService.search(question, ANCHOR_TOP_K, widgetId);
        } catch (Exception e) {
            log.error("[RAG] Qdrant timeout/error: {}", e.getMessage(), e);
            return List.of();
        }

        if (anchors.isEmpty()) {
            log.warn("[RAG] Qdrant returned 0 anchors for widgetId={}", widgetId);
            return List.of();
        }

        Set<UUID> anchorChunkIds = new LinkedHashSet<>();
        Set<String> sectionIds = new LinkedHashSet<>();
        Set<String> tableIds = new LinkedHashSet<>();

        for (TextSegment seg : anchors) {
            String chunkId = seg.metadata().getString("chunk_id");
            if (chunkId != null && !chunkId.isBlank()) {
                try {
                    anchorChunkIds.add(UUID.fromString(chunkId));
                } catch (IllegalArgumentException ignored) {
                }
            }

            String sectionId = seg.metadata().getString("section_id");
            if (sectionId != null && !sectionId.isBlank()) {
                sectionIds.add(sectionId);
            }

            String tableId = seg.metadata().getString("table_id");
            if (tableId != null && !tableId.isBlank()) {
                tableIds.add(tableId);
            }
        }

        log.info("[RAG] Vector anchors: chunks={}, sections={}, tables={}",
                anchorChunkIds.size(), sectionIds.size(), tableIds.size());

        List<DocumentChunk> expanded = new ArrayList<>();
        List<DocumentChunk> lexicalAnchors = findLexicalAnchors(question, widgetId);
        log.info("[RAG] Lexical anchors found: {}", lexicalAnchors.size());

        List<DocumentChunk> sectionExpansion = expandSectionRanges(lexicalAnchors, widgetId, isExpandedQuery);
        log.info("[RAG] Section range expansion: {} chunks", sectionExpansion.size());
        expanded.addAll(sectionExpansion);
        expanded.addAll(expandAroundAnchors(lexicalAnchors, widgetId));

        if (isExpandedQuery) {
            // LIST_ALL / TABLE_LOOKUP / SECTION_SUMMARY: lấy toàn bộ chunk của section/table liên quan
            if (!sectionIds.isEmpty()) {
                List<DocumentChunk> sectionChunks = documentChunkRepository
                        .findByWidgetConfigIdAndSectionIdInOrderByDocumentIdAscOrderIndexAsc(widgetId, sectionIds);
                log.info("[RAG] Section expansion by sectionId: {} chunks from {} sections",
                        sectionChunks.size(), sectionIds.size());
                expanded.addAll(sectionChunks);

                // Sibling expansion: từ sectionIds tìm parentIds → lấy toàn bộ sibling sections
                // Ví dụ: sectionId="sec_6.2" → parentId="parent_6" → lấy cả 6.1, 6.3, 6.4, 6.5
                Set<String> parentIds = deriveParentIds(sectionIds);
                if (!parentIds.isEmpty()) {
                    List<DocumentChunk> siblingChunks = documentChunkRepository
                            .findByWidgetConfigIdAndParentIdInOrderByDocumentIdAscOrderIndexAsc(widgetId, parentIds);
                    log.info("[RAG] Sibling expansion by parentId: {} chunks from {} parents",
                            siblingChunks.size(), parentIds.size());
                    expanded.addAll(siblingChunks);
                }
            }

            if (!tableIds.isEmpty()) {
                List<DocumentChunk> tableChunks = documentChunkRepository
                        .findByWidgetConfigIdAndTableIdInOrderByDocumentIdAscOrderIndexAsc(widgetId, tableIds);
                log.info("[RAG] Table expansion by tableId: {} chunks from {} tables",
                        tableChunks.size(), tableIds.size());
                expanded.addAll(tableChunks);
            }
        } else {
            List<DocumentChunk> anchorChunks =
                    documentChunkRepository.findByWidgetConfigIdAndIdIn(widgetId, anchorChunkIds);

            for (DocumentChunk anchor : anchorChunks) {
                int from = Math.max(0, anchor.getOrderIndex() - WINDOW_BEFORE);
                int to = anchor.getOrderIndex() + WINDOW_AFTER;

                expanded.addAll(documentChunkRepository
                        .findByWidgetConfigIdAndDocumentIdAndOrderIndexBetweenOrderByOrderIndexAsc(
                                widgetId,
                                anchor.getDocument().getId(),
                                from,
                                to
                        ));
            }
        }

        List<RetrievedContext> result = dedupeSortBudget(expanded, queryType);
        log.info("[RAG] Final context: {} chunks, queryType={}", result.size(), queryType);
        return result;
    }

    private List<DocumentChunk> findLexicalAnchors(String question, UUID widgetId) {
        List<String> terms = extractSearchTerms(question);
        if (terms.isEmpty()) {
            return List.of();
        }

        return documentChunkRepository.findByWidgetConfigIdOrderByDocumentIdAscOrderIndexAsc(widgetId)
                .stream()
                .map(chunk -> Map.entry(chunk, lexicalScore(chunk, terms)))
                .filter(entry -> entry.getValue() > 0)
                .sorted(Map.Entry.<DocumentChunk, Integer>comparingByValue().reversed()
                        .thenComparing(entry -> Optional.ofNullable(entry.getKey().getOrderIndex()).orElse(0)))
                .limit(8)
                .map(Map.Entry::getKey)
                .toList();
    }

    private List<DocumentChunk> expandSectionRanges(List<DocumentChunk> anchors, UUID widgetId,
                                                     boolean isExpandedQuery) {
        if (anchors == null || anchors.isEmpty()) {
            return List.of();
        }

        List<DocumentChunk> allChunks =
                documentChunkRepository.findByWidgetConfigIdOrderByDocumentIdAscOrderIndexAsc(widgetId);
        if (allChunks.isEmpty()) {
            return List.of();
        }

        Map<UUID, List<DocumentChunk>> byDocument = allChunks.stream()
                .filter(chunk -> chunk.getDocument() != null && chunk.getDocument().getId() != null)
                .collect(Collectors.groupingBy(
                        chunk -> chunk.getDocument().getId(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        // Pre-build index: docId → (sectionId → sorted list of positions trong docChunks)
        // Tách theo document để tránh nhầm position giữa các document khác nhau
        Map<UUID, Map<String, List<Integer>>> docSectionIndex = new HashMap<>();
        for (Map.Entry<UUID, List<DocumentChunk>> docEntry : byDocument.entrySet()) {
            UUID docId = docEntry.getKey();
            List<DocumentChunk> docChunks = docEntry.getValue();
            Map<String, List<Integer>> sectionPositions = new HashMap<>();
            for (int i = 0; i < docChunks.size(); i++) {
                String sid = docChunks.get(i).getSectionId();
                if (sid != null && !sid.isBlank()) {
                    sectionPositions.computeIfAbsent(sid, k -> new ArrayList<>()).add(i);
                }
            }
            docSectionIndex.put(docId, sectionPositions);
        }

        List<DocumentChunk> result = new ArrayList<>();
        Set<String> expandedSectionKeys = new HashSet<>(); // docId:sectionId
        Set<String> expandedRanges = new HashSet<>();
        int maxChunks = isExpandedQuery ? SECTION_EXPANSION_MAX_CHUNKS_EXPANDED : SECTION_EXPANSION_MAX_CHUNKS;

        for (DocumentChunk anchor : anchors) {
            if (anchor.getDocument() == null || anchor.getDocument().getId() == null) {
                continue;
            }

            UUID docId = anchor.getDocument().getId();
            List<DocumentChunk> documentChunks = byDocument.get(docId);
            if (documentChunks == null || documentChunks.isEmpty()) {
                continue;
            }

            // Ưu tiên 1: dùng sectionId từ metadata (chính xác, không cần regex)
            String anchorSectionId = anchor.getSectionId();
            String sectionKey = docId + ":" + anchorSectionId;
            if (anchorSectionId != null && !anchorSectionId.isBlank()
                    && expandedSectionKeys.add(sectionKey)) {
                Map<String, List<Integer>> sectionPositions = docSectionIndex.get(docId);
                List<Integer> positions = sectionPositions != null
                        ? sectionPositions.get(anchorSectionId) : null;
                if (positions != null && !positions.isEmpty()) {
                    int addCount = 0;
                    for (int pos : positions) {
                        result.add(documentChunks.get(pos));
                        if (++addCount >= maxChunks) break;
                    }
                    log.debug("[RAG] SectionId '{}' (doc={}) → {} chunks added",
                            anchorSectionId, docId, addCount);
                    continue;
                }
            }

            // Fallback: regex scan nếu sectionId không có hoặc không tìm được vị trí
            SectionRange range = findContainingSectionRange(documentChunks, anchor, maxChunks);
            if (range == null) {
                continue;
            }

            String rangeKey = docId + ":" + range.startIndex + ":" + range.endIndex;
            if (!expandedRanges.add(rangeKey)) {
                continue;
            }

            for (int i = range.startIndex; i <= range.endIndex; i++) {
                result.add(documentChunks.get(i));
            }
        }

        return result;
    }

    private SectionRange findContainingSectionRange(List<DocumentChunk> chunks, DocumentChunk anchor,
                                                    int maxChunks) {
        int anchorPosition = findChunkPosition(chunks, anchor);
        if (anchorPosition < 0) {
            return null;
        }

        // Bước 1: Tìm heading gần nhất (từ vị trí anchor lùi về trước)
        HeadingInfo anchorHeading = null;
        int start = anchorPosition;

        for (int i = anchorPosition; i >= 0; i--) {
            HeadingInfo candidate = firstHeading(chunks.get(i));
            if (candidate != null) {
                anchorHeading = candidate;
                start = i;
                break;
            }
        }

        if (anchorHeading == null) {
            return null;
        }

        // Bước 2: Nếu anchor là sub-section (6.2, 6.3...), leo lên tìm parent (6)
        // để mở rộng toàn bộ các sibling (6.1, 6.2, 6.3, 6.4, 6.5)
        if (anchorHeading.level() >= 2) {
            for (int i = start - 1; i >= 0; i--) {
                HeadingInfo parentCandidate = firstHeading(chunks.get(i));
                if (parentCandidate != null && parentCandidate.level() < anchorHeading.level()) {
                    // Xác nhận đây thực sự là parent (prefix match: "6.2" starts with "6.")
                    if (anchorHeading.number.startsWith(parentCandidate.number + ".")) {
                        log.debug("[RAG] Section level-up: {} → parent {}", anchorHeading.number, parentCandidate.number);
                        anchorHeading = parentCandidate;
                        start = i;
                    }
                    break; // Dù có match hay không, chỉ leo lên 1 level
                }
            }
        }

        // Bước 3: Scan forward — dừng khi gặp heading KHÔNG phải con của anchorHeading
        // Tức là: dừng khi nextHeading KHÔNG bắt đầu bằng "anchorHeading.number."
        // Ví dụ: anchorHeading = "6" → tiếp tục qua 6.1, 6.2, 6.3... dừng tại "7"
        int end = Math.min(chunks.size() - 1, start + maxChunks - 1);
        for (int i = start + 1; i < chunks.size(); i++) {
            HeadingInfo nextHeading = firstHeading(chunks.get(i));
            if (nextHeading != null) {
                boolean isChildOrSelf = nextHeading.number.equals(anchorHeading.number)
                        || nextHeading.number.startsWith(anchorHeading.number + ".");
                if (!isChildOrSelf && nextHeading.level() <= anchorHeading.level()) {
                    // Gặp section cùng cấp hoặc cao hơn không phải con → kết thúc range
                    end = Math.max(start, i - 1);
                    break;
                }
            }

            if (i - start + 1 >= maxChunks) {
                end = i;
                break;
            }
        }

        return new SectionRange(start, end);
    }

    private int findChunkPosition(List<DocumentChunk> chunks, DocumentChunk target) {
        UUID targetId = target.getId();
        for (int i = 0; i < chunks.size(); i++) {
            if (Objects.equals(chunks.get(i).getId(), targetId)) {
                return i;
            }
        }
        return -1;
    }

    private HeadingInfo firstHeading(DocumentChunk chunk) {
        String content = normalizeForSearch(chunk.getContent());
        Matcher matcher = HEADING_PATTERN.matcher(content);
        while (matcher.find()) {
            String number = matcher.group(1);
            String title = matcher.group(2).trim();
            if (!isLikelyHeadingTitle(title)) {
                continue;
            }

            return new HeadingInfo(number, title);
        }
        return null;
    }

    private boolean isLikelyHeadingTitle(String title) {
        String normalized = title == null ? "" : title.trim();
        if (normalized.length() < 4) {
            return false;
        }

        return !normalized.matches(".*\\b(phut|ngay|request|page|trang)\\b.*");
    }

    private List<DocumentChunk> expandAroundAnchors(List<DocumentChunk> anchors, UUID widgetId) {
        if (anchors == null || anchors.isEmpty()) {
            return List.of();
        }

        List<DocumentChunk> result = new ArrayList<>();
        for (DocumentChunk anchor : anchors) {
            int from = Math.max(0, anchor.getOrderIndex() - WINDOW_BEFORE);
            int to = anchor.getOrderIndex() + WINDOW_AFTER;
            result.addAll(documentChunkRepository
                    .findByWidgetConfigIdAndDocumentIdAndOrderIndexBetweenOrderByOrderIndexAsc(
                            widgetId,
                            anchor.getDocument().getId(),
                            from,
                            to
                    ));
        }
        return result;
    }

    private List<String> extractSearchTerms(String question) {
        if (question == null || question.isBlank()) {
            return List.of();
        }

        // Normalize để lexical anchor match được tiếng Việt có dấu/không dấu
        String normalizedQuestion = normalizeForSearch(question);

        // Stopwords phải ở dạng đã normalize (không dấu) vì normalizedQuestion cũng đã normalize
        Set<String> stopWords = Set.of(
                "gom", "nhung", "buoc", "nao",
                "liet", "ke", "cac", "va", "cua",
                "tung", "chinh",
                "trinh", "bay", "bao", "tom", "tat", "tong", "hop",
                "he", "thong", "tai", "lieu", "noi", "dung", "phan", "muc", "chuong"
        );

        return Arrays.stream(normalizedQuestion.split("[^\\p{L}\\p{N}]+"))
                .map(String::trim)
                .filter(term -> term.length() >= 3)
                .filter(term -> !stopWords.contains(term))
                .distinct()
                .toList();
    }

    private String normalizeForSearch(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }

        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace('đ', 'd')
                .replace('Đ', 'D')
                .toLowerCase(Locale.ROOT);

        return normalized.replaceAll("\\s+", " ").trim();
    }

    private int lexicalScore(DocumentChunk chunk, List<String> terms) {
        // Tổng quát: chấm điểm dựa trên cả content + heading/section title
        // để các câu hỏi theo "tiêu đề mục" vẫn match tốt cho nhiều loại tài liệu khác nhau.
        String content = normalizeForSearch(Optional.ofNullable(chunk.getContent()).orElse(""));
        String heading = normalizeForSearch(Optional.ofNullable(chunk.getHeadingPathText()).orElse(""));
        String sectionTitle = normalizeForSearch(Optional.ofNullable(chunk.getSectionTitle()).orElse(""));
        String haystack = (heading + " " + sectionTitle + " " + content).trim();
        int score = 0;
        for (String term : terms) {
            String t = normalizeForSearch(term);
            if (!t.isBlank() && haystack.contains(t)) {
                score++;
            }
        }

        if ("text".equalsIgnoreCase(chunk.getChunkType())) {
            score += Math.min(score, 2);
        }

        return score;
    }

    /**
     * Từ tập sectionIds, suy ra parentIds tương ứng để mở rộng sibling sections.
     *
     * Logic:
     *  - "sec_6.2"     → parentId = "parent_6"     (sub-section: bỏ phần sau dấu chấm cuối)
     *  - "sec_6.1.2"   → parentId = "parent_6.1"
     *  - "sec_6"       → không có parent (bỏ qua)
     *  - "sec_idx_5"   → không có parent cấu trúc (bỏ qua)
     *
     * Chỉ trả về parentIds có ý nghĩa cấu trúc (dạng "parent_X.Y" hoặc "parent_X").
     */
    private Set<String> deriveParentIds(Set<String> sectionIds) {
        Set<String> parentIds = new LinkedHashSet<>();
        for (String sectionId : sectionIds) {
            if (sectionId == null || !sectionId.startsWith("sec_")) continue;
            String number = sectionId.substring("sec_".length()); // "6.2"
            if (number.startsWith("idx_")) continue;              // skip positional ids
            int lastDot = number.lastIndexOf('.');
            if (lastDot > 0) {
                String parentNumber = number.substring(0, lastDot); // "6"
                parentIds.add("parent_" + parentNumber);            // "parent_6"
            }
        }
        return parentIds;
    }

    private record HeadingInfo(String number, String title) {
        int level() {
            return number.split("\\.").length;
        }
    }

    private record SectionRange(int startIndex, int endIndex) {
    }

    private List<RetrievedContext> dedupeSortBudget(List<DocumentChunk> chunks,
                                                    QueryAnalyzerService.QueryType queryType) {
        boolean isExpandedQuery = queryType == QueryAnalyzerService.QueryType.LIST_ALL
                || queryType == QueryAnalyzerService.QueryType.TABLE_LOOKUP
                || queryType == QueryAnalyzerService.QueryType.SECTION_SUMMARY;

        int finalLimit = isExpandedQuery ? FINAL_LIMIT_EXPANDED : FINAL_LIMIT;
        int maxContextChars = isExpandedQuery ? MAX_CONTEXT_CHARS_EXPANDED : MAX_CONTEXT_CHARS;

        Map<UUID, DocumentChunk> unique = chunks.stream()
                .filter(c -> c.getId() != null)
                .collect(Collectors.toMap(
                        DocumentChunk::getId,
                        c -> c,
                        (a, b) -> a,
                        LinkedHashMap::new
                ));

        List<RetrievedContext> result = new ArrayList<>();
        int totalChars = 0;

        for (DocumentChunk c : unique.values()) {
            if (result.size() >= finalLimit) {
                log.debug("[RAG] Budget: reached finalLimit={}", finalLimit);
                break;
            }

            String content = c.getContent() == null ? "" : c.getContent();
            if (totalChars + content.length() > maxContextChars) {
                log.debug("[RAG] Budget: reached maxContextChars={} at chunk #{}", maxContextChars, result.size());
                break;
            }

            totalChars += content.length();

            result.add(RetrievedContext.builder()
                    .chunkId(c.getId())
                    .documentId(c.getDocument().getId())
                    .fileName(c.getSourceFile())
                    .content(content)
                    .chunkType(c.getChunkType())
                    .sectionId(c.getSectionId())
                    .sectionTitle(c.getSectionTitle())
                    .headingPathText(c.getHeadingPathText())
                    .pageStart(c.getPageStart())
                    .pageEnd(c.getPageEnd())
                    .build());
        }

        log.info("[RAG] dedupeSortBudget: input={}, unique={}, output={}, totalChars={}, limit={}/{}",
                chunks.size(), unique.size(), result.size(), totalChars, finalLimit, maxContextChars);

        return result;
    }
}
