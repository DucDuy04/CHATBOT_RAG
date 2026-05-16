package KLTN.RAG_CHATBOT_BE.service;

import KLTN.RAG_CHATBOT_BE.domain.document.DocumentChunk;
import KLTN.RAG_CHATBOT_BE.domain.document.DocumentChunkRepository;
import KLTN.RAG_CHATBOT_BE.domain.document.DocumentSection;
import KLTN.RAG_CHATBOT_BE.domain.document.DocumentSectionRepository;
import KLTN.RAG_CHATBOT_BE.dto.RetrievedContext;
import dev.langchain4j.data.segment.TextSegment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagRetrievalService {

    /** Default Qdrant vector candidate limit when request does not specify topK. */
    public static final int DEFAULT_ANCHOR_TOP_K = 30;

    public static final int MIN_ANCHOR_TOP_K = 1;

    public static final int MAX_ANCHOR_TOP_K = 30;

    // --- Giới hạn cho query thông thường ---
    private static final int FINAL_LIMIT = 10;
    private static final int MAX_CONTEXT_CHARS = 18_000;

    // --- Giới hạn cho query LIST_ALL / TABLE / SECTION_SUMMARY / COUNT_QUERY ---
    private static final int FINAL_LIMIT_EXPANDED = 20;
    private static final int MAX_CONTEXT_CHARS_EXPANDED = 32_000;

    // --- Giới hạn khi scope đã lock vào heading cụ thể (ưu tiên toàn bộ section) ---
    private static final int FINAL_LIMIT_LOCKED = 60;
    private static final int MAX_CONTEXT_CHARS_LOCKED = 64_000;

    // --- Window mở rộng quanh anchor ---
    private static final int WINDOW_BEFORE = 1;
    private static final int WINDOW_AFTER = 2;

    // --- Giới hạn expansion theo section ---
    private static final int SECTION_EXPANSION_MAX_CHUNKS = 12;
    private static final int SECTION_EXPANSION_MAX_CHUNKS_EXPANDED = 30;

    // Ngưỡng titleHits để kích hoạt heading lock
    private static final int HEADING_LOCK_MIN_TITLE_HITS = 2;

    private static final Pattern HEADING_PATTERN =
            Pattern.compile("(?<!\\d)(\\d+(?:\\.\\d+)*)\\.?\\s+([\\p{L}][\\p{L}\\p{N}\\s/&+\\-()]{3,120})");

    /**
     * Ngưỡng rerank score tối thiểu để kích hoạt "Rerank-Guided Scope Lock".
     *
     * Khi heading match ban đầu thất bại nhưng reranker trả về top chunk với score ≥ ngưỡng này,
     * hệ thống sẽ tự động lock scope về section đó và re-fetch từ DB.
     *
     * Chọn 0.5 vì Cohere cross-encoder score ≥ 0.5 thể hiện độ tin cậy cao
     * (ví dụ sec_2=0.8658 >> sec_1=0.0497 → sec_2 rõ ràng là đúng).
     */
    private static final double RERANK_LOCK_THRESHOLD = 0.5;

    private final EmbeddingService embeddingService;
    private final DocumentChunkRepository documentChunkRepository;
    private final DocumentSectionRepository documentSectionRepository;
    private final QueryAnalyzerService queryAnalyzerService;
    private final RerankService rerankService;

    // ================================================================
    // RESULT WRAPPER
    // ================================================================

    /**
     * Bao gồm danh sách context và metadata về scope được lock (nếu có).
     *
     * @param contexts         Danh sách chunk context cuối cùng.
     * @param lockedScopeLabel Nhãn mô tả scope đã lock (null nếu không lock).
     *                         Ví dụ: "'6.2 Kiến trúc' [sec_6.2]"
     */
    public record RetrievalResult(List<RetrievedContext> contexts, String lockedScopeLabel) {}

    // ================================================================
    // PUBLIC ENTRY POINT
    // ================================================================

    /** Backward-compatible entry point — returns only the context list. */
    public List<RetrievedContext> retrieve(String question, UUID widgetId) {
        return retrieveWithMetadata(question, widgetId, null).contexts();
    }

    /** Full entry point — default anchor top-K (30). */
    public RetrievalResult retrieveWithMetadata(String question, UUID widgetId) {
        return retrieveWithMetadata(question, widgetId, null);
    }

    /**
     * Full entry point with optional per-request anchor top-K override for Qdrant vector search.
     *
     * @param topKOverride requested limit from client; null uses {@link #DEFAULT_ANCHOR_TOP_K}
     */
    public RetrievalResult retrieveWithMetadata(String question, UUID widgetId, Integer topKOverride) {
        int effectiveAnchorTopK = normalizeAnchorTopK(topKOverride);
        log.info("[RAG] retrieval topK requested={}, effective={}", topKOverride, effectiveAnchorTopK);

        // ── STEP 0: Intent detection ───────────────────────────────────
        QueryAnalyzerService.QueryType queryType = queryAnalyzerService.analyze(question, widgetId);
        boolean isExpandedQuery = isExpanded(queryType);
        log.info("[RAG] Detected intent: question='{}' queryType={} widgetId={}", question, queryType, widgetId);

        // ── STEP 1: Fetch all sections once (shared by heading match + scope expansion) ──
        List<DocumentSection> allSections =
                documentSectionRepository.findByWidgetConfigIdOrderByOrderIndexAsc(widgetId);

        // ── STEP 2: Heading-first match ────────────────────────────────
        // Pass pre-fetched sections to avoid a second DB round-trip.
        // findMatchedSections() returns matches sorted BEST-FIRST (highest score at index 0).
        List<QueryAnalyzerService.HeadingMatch> headingMatches =
                queryAnalyzerService.findMatchedSections(question, allSections);

        // Among qualified matches, pick the most specific section for scope locking.
        // When a child and its parent both qualify, the child is preferred because:
        //   (a) the new scoring penalises missed terms — child with more specific title has higher score,
        //   (b) the depth tiebreaker in sorting pushes deeper sections above shallow ones on ties.
        // selectMostSpecificMatch() handles any residual ambiguity.
        QueryAnalyzerService.HeadingMatch selectedMatch = selectMostSpecificMatch(headingMatches);

        String lockedSectionKey   = null;
        String lockedSectionLabel = null;
        Set<String> lockedSectionIds = Set.of();

        if (selectedMatch != null && selectedMatch.titleHits() >= HEADING_LOCK_MIN_TITLE_HITS) {
            lockedSectionKey  = selectedMatch.sectionKey();
            lockedSectionLabel = "'" + selectedMatch.title() + "' [" + selectedMatch.sectionKey() + "]";

            // Expand scope to all descendants of the selected section.
            // For a leaf child → only that section. For a parent → all children too.
            lockedSectionIds = expandDescendantSectionIds(lockedSectionKey, allSections);

            log.info("[RAG] Selected section: {} | Locked scope: {} section(s): {}",
                    lockedSectionLabel, lockedSectionIds.size(), lockedSectionIds);
        } else {
            log.info("[RAG] No heading lock applied (best titleHits={}, threshold={})",
                    selectedMatch == null ? 0 : selectedMatch.titleHits(), HEADING_LOCK_MIN_TITLE_HITS);
        }

        // ── STEP 2: Query rewriting for semantic search ────────────────
        List<String> queryVariants = queryAnalyzerService.rewriteQuery(question);
        log.info("[RAG] Query variants: {}", queryVariants);

        // ── STEP 3: Vector search (all variants) ──────────────────────
        Set<UUID> anchorChunkIds = new LinkedHashSet<>();
        Set<String> sectionIds = new LinkedHashSet<>();
        Set<String> tableIds = new LinkedHashSet<>();
        Set<UUID> vectorDocumentIds = new LinkedHashSet<>();
        int excludedByScope = 0;

        for (String variant : queryVariants) {
            List<TextSegment> anchors;
            try {
                anchors = embeddingService.search(variant, effectiveAnchorTopK, widgetId);
            } catch (Exception e) {
                log.error("[RAG] Qdrant error for variant='{}': {}", variant, e.getMessage());
                continue;
            }

            for (TextSegment seg : anchors) {
                String chunkSectionId = seg.metadata().getString("section_id");

                // When scope is locked, filter vector results to only chunks inside scope.
                // Semantic search is supplementary — it cannot override a heading match.
                if (!lockedSectionIds.isEmpty() && chunkSectionId != null
                        && !lockedSectionIds.contains(chunkSectionId)) {
                    excludedByScope++;
                    log.debug("[RAG] EXCLUDED (out-of-scope): sectionId='{}' not in lockedScope", chunkSectionId);
                    continue;
                }

                extractIds(seg, anchorChunkIds, sectionIds, tableIds, vectorDocumentIds);

                // Auto-expand from parent_section_summary → all child sections
                String chunkType = seg.metadata().getString("chunk_type");
                String childSectionIdsStr = seg.metadata().getString("child_section_ids");
                if ("parent_section_summary".equals(chunkType)
                        && childSectionIdsStr != null && !childSectionIdsStr.isBlank()) {
                    Arrays.stream(childSectionIdsStr.split(","))
                            .map(String::trim).filter(s -> !s.isBlank())
                            .forEach(sectionIds::add);
                    log.info("[RAG] parent_section_summary matched → expanding to children: [{}]",
                            childSectionIdsStr);
                }
            }
        }

        if (excludedByScope > 0) {
            log.info("[RAG] Semantic results excluded (out of locked scope): {} segments", excludedByScope);
        }

        log.info("[RAG] Vector anchors: chunks={} sections={} tables={} docs={}",
                anchorChunkIds.size(), sectionIds.size(), tableIds.size(), vectorDocumentIds.size());

        // ── STEP 4: Build context pool ─────────────────────────────────
        List<DocumentChunk> expanded = new ArrayList<>();

        if (!lockedSectionIds.isEmpty()) {
            // ── LOCKED-SCOPE MODE ──
            // Fetch ALL chunks from the locked section tree directly from DB.
            // This guarantees completeness for list/count/overview queries.
            List<DocumentChunk> lockedChunks = documentChunkRepository
                    .findByWidgetConfigIdAndSectionIdInOrderByDocumentIdAscOrderIndexAsc(
                            widgetId, lockedSectionIds);
            log.info("[RAG] Locked scope: {} chunks fetched from DB for sectionIds={}",
                    lockedChunks.size(), lockedSectionIds);

            // ── GUARDRAIL: if locked scope found 0 chunks, the heading match may be stale ──
            // (e.g. section exists in DocumentSection but no chunks indexed yet, or key mismatch)
            // Fall back to semantic mode and clear the lock so the prompt is not mis-scoped.
            if (lockedChunks.isEmpty()) {
                log.warn("[RAG] GUARDRAIL: locked scope {} returned 0 chunks — " +
                        "falling back to semantic retrieval. Possible cause: section key mismatch " +
                        "or section not yet indexed. lockedSectionIds={}", lockedSectionKey, lockedSectionIds);
                lockedSectionKey   = null;
                lockedSectionLabel = null;
                lockedSectionIds   = Set.of();
                // Fall through to the semantic path below (expanded is still empty)
            } else {
                expanded.addAll(lockedChunks);

                // Also pull any table chunks that vector search found within scope
                if (!tableIds.isEmpty()) {
                    List<DocumentChunk> tableChunks = documentChunkRepository
                            .findByWidgetConfigIdAndTableIdInOrderByDocumentIdAscOrderIndexAsc(
                                    widgetId, tableIds);
                    log.info("[RAG] Table expansion (locked mode): {} chunks from {} tableIds",
                            tableChunks.size(), tableIds.size());
                    expanded.addAll(tableChunks);
                }
            }
        }

        // ── POST-GUARDRAIL CHECK: verify no context chunk comes from outside the locked scope ──
        if (lockedSectionKey != null && !expanded.isEmpty()) {
            final Set<String> finalLockedIds = lockedSectionIds;
            long outOfScopeCount = expanded.stream()
                    .filter(c -> c.getSectionId() != null
                            && !finalLockedIds.contains(c.getSectionId()))
                    .count();
            if (outOfScopeCount > 0) {
                log.warn("[RAG] GUARDRAIL: {} chunk(s) in expanded pool are outside locked scope {} — " +
                        "will be excluded by dedupeSortBudget scope filter.",
                        outOfScopeCount, lockedSectionKey);
            }
        }

        if (expanded.isEmpty() && vectorDocumentIds.isEmpty() && anchorChunkIds.isEmpty()) {
            log.warn("[RAG] Qdrant returned 0 anchors and locked scope empty for widgetId={}", widgetId);
            return new RetrievalResult(List.of(), null);
        }

        if (expanded.isEmpty()) {
            // ── FALLBACK/SEMANTIC MODE ──
            // Reached when: (a) no heading lock, or (b) locked scope guardrail cleared the lock.
            List<DocumentChunk> lexicalAnchors = findLexicalAnchors(question, widgetId, vectorDocumentIds);
            log.info("[RAG] Lexical anchors found: {}", lexicalAnchors.size());

            List<DocumentChunk> sectionExpansion = expandSectionRanges(
                    lexicalAnchors, widgetId, isExpandedQuery, vectorDocumentIds);
            log.info("[RAG] Section range expansion: {} chunks", sectionExpansion.size());
            expanded.addAll(sectionExpansion);
            expanded.addAll(expandAroundAnchors(lexicalAnchors, widgetId));

            if (isExpandedQuery) {
                if (!sectionIds.isEmpty()) {
                    List<DocumentChunk> sectionChunks = documentChunkRepository
                            .findByWidgetConfigIdAndSectionIdInOrderByDocumentIdAscOrderIndexAsc(
                                    widgetId, sectionIds);
                    log.info("[RAG] Section expansion by sectionId: {} chunks from {} sections",
                            sectionChunks.size(), sectionIds.size());
                    expanded.addAll(sectionChunks);

                    // Sibling expansion: find parentIds → pull sibling sections
                    Set<String> parentIds = deriveParentIds(sectionIds);
                    if (!parentIds.isEmpty()) {
                        List<DocumentChunk> siblingChunks = documentChunkRepository
                                .findByWidgetConfigIdAndParentIdInOrderByDocumentIdAscOrderIndexAsc(
                                        widgetId, parentIds);
                        log.info("[RAG] Sibling expansion by parentId: {} chunks from {} parents",
                                siblingChunks.size(), parentIds.size());
                        expanded.addAll(siblingChunks);
                    }

                    // Parent section summaries
                    if (!vectorDocumentIds.isEmpty()) {
                        List<DocumentChunk> parentSummaryChunks =
                                findParentSectionSummaries(sectionIds, widgetId, vectorDocumentIds);
                        log.info("[RAG] Parent section summaries: {} chunks", parentSummaryChunks.size());
                        expanded.addAll(parentSummaryChunks);
                    }
                }

                if (!tableIds.isEmpty()) {
                    List<DocumentChunk> tableChunks = documentChunkRepository
                            .findByWidgetConfigIdAndTableIdInOrderByDocumentIdAscOrderIndexAsc(
                                    widgetId, tableIds);
                    log.info("[RAG] Table expansion by tableId: {} chunks from {} tables",
                            tableChunks.size(), tableIds.size());
                    expanded.addAll(tableChunks);
                }
            } else {
                // Normal query: window expansion around vector anchors
                List<DocumentChunk> anchorChunks =
                        documentChunkRepository.findByWidgetConfigIdAndIdIn(widgetId, anchorChunkIds);

                for (DocumentChunk anchor : anchorChunks) {
                    int from = Math.max(0, anchor.getOrderIndex() - WINDOW_BEFORE);
                    int to = anchor.getOrderIndex() + WINDOW_AFTER;
                    expanded.addAll(documentChunkRepository
                            .findByWidgetConfigIdAndDocumentIdAndOrderIndexBetweenOrderByOrderIndexAsc(
                                    widgetId, anchor.getDocument().getId(), from, to));
                }
            }
        }

        // ── STEP 5: Rerank — chấm điểm lại từng cặp (query, chunk) bằng Cross-Encoder ──
        // topN = FINAL_LIMIT * 1.3 (buffer nhỏ) để reranker thực sự lọc bớt,
        // không phải giữ nguyên toàn bộ pool như cũ (topN = FINAL_LIMIT * 2).
        boolean isLockedScope = !lockedSectionIds.isEmpty();
        if (!isLockedScope && rerankService.isEnabled() && !expanded.isEmpty()) {
            int baseLimit = isExpandedQuery ? FINAL_LIMIT_EXPANDED : FINAL_LIMIT;
            int rerankTopN = (int) Math.ceil(baseLimit * 1.3);
            RerankService.RerankResult rerankResult = rerankService.rerank(question, expanded, rerankTopN);
            expanded = rerankResult.chunks();
            log.info("[RAG] Sau rerank: {} chunks (maxScore={}, minScore={})",
                    expanded.size(),
                    String.format("%.4f", rerankResult.maxScore()),
                    String.format("%.4f", rerankResult.minScore()));

            if (rerankResult.maxScore() < RerankService.LOW_CONFIDENCE_THRESHOLD) {
                log.warn("[RAG] LOW CONFIDENCE: max rerank score={} < threshold={} cho query='{}'. " +
                        "Có thể query có typo, quá mơ hồ, hoặc tài liệu không chứa thông tin này.",
                        String.format("%.4f", rerankResult.maxScore()),
                        RerankService.LOW_CONFIDENCE_THRESHOLD, question);
            }

            // ── STEP 5.5: Rerank-Guided Scope Lock ────────────────────────
            // Khi heading match ban đầu thất bại (isLockedScope=false) NHƯNG
            // reranker tìm thấy top chunk với score rất cao (≥ RERANK_LOCK_THRESHOLD),
            // đây là tín hiệu rõ ràng về section đúng → lock scope về section đó
            // và re-fetch toàn bộ chunks từ section tree để context sạch hơn.
            //
            // Ví dụ (từ log):
            //   query: "mục tiêu của hệ thống" → heading match: FAIL
            //   rerank top: sec_2=0.8658, sec_1=0.0497, sec_7.2=0.0058
            //   → lock sec_2 → re-fetch [sec_2, sec_2.1, sec_2.2] = 3 chunks
            //   INSTEAD OF 20 chunks từ 33 sections trên toàn tài liệu.
            //
            // MVP guard: KHÔNG kích hoạt lock cho câu hỏi tổng hợp (list/count/table),
            // vì top-1 rerank thường là một use case / một bảng con → lock sẽ loại bỏ
            // các chunk còn lại và bot chỉ trả một phần (vd chỉ Use Case 4).
            boolean allowRerankScopeLock = queryType != QueryAnalyzerService.QueryType.LIST_ALL
                    && queryType != QueryAnalyzerService.QueryType.COUNT_QUERY
                    && queryType != QueryAnalyzerService.QueryType.TABLE_LOOKUP;
            if (allowRerankScopeLock
                    && rerankResult.maxScore() >= RERANK_LOCK_THRESHOLD && !rerankResult.chunks().isEmpty()) {
                DocumentChunk topChunk = rerankResult.chunks().get(0);
                String topSectionId = topChunk.getSectionId();

                if (topSectionId != null && !topSectionId.isBlank()) {
                    // Leo lên 1 cấp nếu top chunk là leaf (sec_2.1 → sec_2),
                    // giữ nguyên nếu đã là root (sec_2 → sec_2).
                    String lockRootId = findReasonableLockRoot(topSectionId, allSections);
                    Set<String> rerankScope = expandDescendantSectionIds(lockRootId, allSections);

                    List<DocumentChunk> rerankScopeChunks = documentChunkRepository
                            .findByWidgetConfigIdAndSectionIdInOrderByDocumentIdAscOrderIndexAsc(
                                    widgetId, rerankScope);

                    if (!rerankScopeChunks.isEmpty()) {
                        log.info("[RAG] Rerank-Guided Lock ACTIVATED: score={} topSection='{}' " +
                                "→ lockRoot='{}' scope={} ({} chunks) — replaced {} chunk pool",
                                String.format("%.4f", rerankResult.maxScore()),
                                topSectionId, lockRootId, rerankScope,
                                rerankScopeChunks.size(), expanded.size());
                        expanded = rerankScopeChunks;
                        isLockedScope = true;
                        lockedSectionLabel = "rerank-lock: '" + lockRootId + "'";
                    } else {
                        log.warn("[RAG] Rerank-Guided Lock: lockRoot='{}' returned 0 chunks — " +
                                "keeping reranked pool as-is.", lockRootId);
                    }
                }
            }
        }

        // ── STEP 6: Dedup, sort, apply budget ─────────────────────────
        List<RetrievedContext> result = dedupeSortBudget(expanded, queryType, isLockedScope, question);

        // ── STEP 7: Final context log ──────────────────────────────────
        log.info("[RAG] Final context chunks: {} | queryType={} | lockedScope={}",
                result.size(), queryType, lockedSectionKey != null ? lockedSectionKey : "none");
        if (!result.isEmpty()) {
            log.info("[RAG] Context chunk list: {}",
                    result.stream()
                            .map(r -> r.getSectionId() + "[" + r.getChunkType() + "]")
                            .toList());
        }

        return new RetrievalResult(result, lockedSectionLabel);
    }

    // ================================================================
    // HEADING LOCK HELPERS
    // ================================================================

    /**
     * Chọn section cụ thể nhất (most-specific) trong danh sách candidates đã sắp xếp.
     *
     * Quy tắc:
     * 1. Bắt đầu từ best = candidates.get(0) (match tốt nhất theo score).
     * 2. Duyệt các candidates còn lại — nếu tìm thấy một child section của best
     *    có score gần bằng best (trong khoảng SPECIFICITY_SCORE_TOLERANCE điểm) → chọn child đó.
     *    Tức là: nếu parent và child đều match tốt, ưu tiên child cụ thể hơn.
     * 3. Nếu score chênh lệch nhiều → không can thiệp, giữ best từ sorting.
     *
     * Lý do cần hàm này (dù đã có depth tiebreaker trong sort):
     * Trường hợp edge: parent có exact phrase bonus rất cao nhưng child thực sự match tốt hơn —
     * hàm này bắt trường hợp đó và ưu tiên child nếu score không chênh quá nhiều.
     */
    private QueryAnalyzerService.HeadingMatch selectMostSpecificMatch(
            List<QueryAnalyzerService.HeadingMatch> candidates) {
        if (candidates == null || candidates.isEmpty()) return null;

        QueryAnalyzerService.HeadingMatch best = candidates.get(0);
        final int SPECIFICITY_SCORE_TOLERANCE = 3; // nếu child kém hơn <= 3 điểm → vẫn ưu tiên child

        for (int i = 1; i < candidates.size(); i++) {
            QueryAnalyzerService.HeadingMatch candidate = candidates.get(i);

            // Score của candidate không được kém quá xa so với best
            if (best.totalScore() - candidate.totalScore() > SPECIFICITY_SCORE_TOLERANCE) break;

            // candidate phải là CHILD (descendant) của best: sectionKey bắt đầu bằng best.sectionKey() + "."
            boolean isChildOfBest = candidate.sectionKey().startsWith(best.sectionKey() + ".");

            if (isChildOfBest) {
                log.info("[RAG] selectMostSpecificMatch: preferring child '{}' [{}] (score={}) " +
                                "over parent '{}' [{}] (score={}) — child is more specific",
                        candidate.title(), candidate.sectionKey(), candidate.totalScore(),
                        best.title(), best.sectionKey(), best.totalScore());
                best = candidate;
            }
        }

        return best;
    }

    /**
     * Mở rộng sectionKey gốc sang toàn bộ section con (descendants).
     *
     * Ví dụ: rootSectionKey="sec_6" → kết quả = {"sec_6", "sec_6.1", "sec_6.2", "sec_6.1.1", …}
     * Dựa trên quy ước sectionKey = "sec_" + sectionNumber (ví dụ "sec_6.1.2").
     */
    private Set<String> expandDescendantSectionIds(String rootSectionKey,
                                                    List<DocumentSection> allSections) {
        Set<String> result = new LinkedHashSet<>();
        String prefix = rootSectionKey + ".";     // ví dụ: "sec_6."

        for (DocumentSection sec : allSections) {
            String key = sec.getSectionKey();
            if (key == null) continue;
            if (key.equals(rootSectionKey) || key.startsWith(prefix)) {
                result.add(key);
            }
        }

        log.debug("[RAG] expandDescendantSectionIds('{}') → {} sections: {}",
                rootSectionKey, result.size(), result);
        return result;
    }

    // ================================================================
    // PARENT SECTION SUMMARIES
    // ================================================================

    /**
     * Tìm section_summary chunk của section cha cho các leaf sectionIds.
     * Ví dụ: sectionIds chứa "sec_6.2" → tìm chunk có sectionId="sec_6" và chunkType="section_summary".
     */
    private List<DocumentChunk> findParentSectionSummaries(Set<String> sectionIds,
                                                            UUID widgetId,
                                                            Set<UUID> vectorDocumentIds) {
        Set<String> parentSectionIds = new LinkedHashSet<>();
        for (String sid : sectionIds) {
            if (sid == null || !sid.startsWith("sec_")) continue;
            String number = sid.substring("sec_".length());
            if (number.startsWith("idx_")) continue;
            int lastDot = number.lastIndexOf('.');
            if (lastDot > 0) {
                parentSectionIds.add("sec_" + number.substring(0, lastDot));
            }
        }

        if (parentSectionIds.isEmpty()) return List.of();

        List<DocumentChunk> candidates = documentChunkRepository
                .findByWidgetConfigIdAndSectionIdInOrderByDocumentIdAscOrderIndexAsc(
                        widgetId, parentSectionIds);

        return candidates.stream()
                .filter(c -> "section_summary".equals(c.getChunkType())
                        || "text".equals(c.getChunkType()))
                .limit(6)
                .toList();
    }

    // ================================================================
    // LEXICAL ANCHORS
    // ================================================================

    private List<DocumentChunk> findLexicalAnchors(String question, UUID widgetId,
                                                    Set<UUID> vectorDocumentIds) {
        List<String> terms = extractSearchTerms(question);
        if (terms.isEmpty() || vectorDocumentIds.isEmpty()) {
            return List.of();
        }

        return documentChunkRepository
                .findByWidgetConfigIdAndDocumentIdInOrderByDocumentIdAscOrderIndexAsc(
                        widgetId, vectorDocumentIds)
                .stream()
                .map(chunk -> Map.entry(chunk, lexicalScore(chunk, terms)))
                .filter(entry -> entry.getValue() > 0)
                .sorted(Map.Entry.<DocumentChunk, Integer>comparingByValue().reversed()
                        .thenComparing(entry ->
                                Optional.ofNullable(entry.getKey().getOrderIndex()).orElse(0)))
                .limit(8)
                .map(Map.Entry::getKey)
                .toList();
    }

    // ================================================================
    // SECTION RANGE EXPANSION
    // ================================================================

    private List<DocumentChunk> expandSectionRanges(List<DocumentChunk> anchors, UUID widgetId,
                                                     boolean isExpandedQuery,
                                                     Set<UUID> vectorDocumentIds) {
        if (anchors == null || anchors.isEmpty()) return List.of();

        Set<UUID> scopedDocIds = new LinkedHashSet<>(vectorDocumentIds);
        for (DocumentChunk anchor : anchors) {
            if (anchor.getDocument() != null && anchor.getDocument().getId() != null) {
                scopedDocIds.add(anchor.getDocument().getId());
            }
        }

        if (scopedDocIds.isEmpty()) return List.of();

        List<DocumentChunk> allChunks =
                documentChunkRepository.findByWidgetConfigIdAndDocumentIdInOrderByDocumentIdAscOrderIndexAsc(
                        widgetId, scopedDocIds);
        if (allChunks.isEmpty()) return List.of();

        Map<UUID, List<DocumentChunk>> byDocument = allChunks.stream()
                .filter(chunk -> chunk.getDocument() != null && chunk.getDocument().getId() != null)
                .collect(Collectors.groupingBy(
                        chunk -> chunk.getDocument().getId(),
                        LinkedHashMap::new,
                        Collectors.toList()));

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
        Set<String> expandedSectionKeys = new HashSet<>();
        Set<String> expandedRanges = new HashSet<>();
        int maxChunks = isExpandedQuery ? SECTION_EXPANSION_MAX_CHUNKS_EXPANDED : SECTION_EXPANSION_MAX_CHUNKS;

        for (DocumentChunk anchor : anchors) {
            if (anchor.getDocument() == null || anchor.getDocument().getId() == null) continue;

            UUID docId = anchor.getDocument().getId();
            List<DocumentChunk> documentChunks = byDocument.get(docId);
            if (documentChunks == null || documentChunks.isEmpty()) continue;

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

            SectionRange range = findContainingSectionRange(documentChunks, anchor, maxChunks);
            if (range == null) continue;

            String rangeKey = docId + ":" + range.startIndex + ":" + range.endIndex;
            if (!expandedRanges.add(rangeKey)) continue;

            for (int i = range.startIndex; i <= range.endIndex; i++) {
                result.add(documentChunks.get(i));
            }
        }

        return result;
    }

    private SectionRange findContainingSectionRange(List<DocumentChunk> chunks,
                                                    DocumentChunk anchor, int maxChunks) {
        int anchorPosition = findChunkPosition(chunks, anchor);
        if (anchorPosition < 0) return null;

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

        if (anchorHeading == null) return null;

        // Leo lên tìm parent nếu anchor là sub-section
        if (anchorHeading.level() >= 2) {
            for (int i = start - 1; i >= 0; i--) {
                HeadingInfo parentCandidate = firstHeading(chunks.get(i));
                if (parentCandidate != null && parentCandidate.level() < anchorHeading.level()) {
                    if (anchorHeading.number.startsWith(parentCandidate.number + ".")) {
                        log.debug("[RAG] Section level-up: {} → parent {}",
                                anchorHeading.number, parentCandidate.number);
                        anchorHeading = parentCandidate;
                        start = i;
                    }
                    break;
                }
            }
        }

        int end = Math.min(chunks.size() - 1, start + maxChunks - 1);
        for (int i = start + 1; i < chunks.size(); i++) {
            HeadingInfo nextHeading = firstHeading(chunks.get(i));
            if (nextHeading != null) {
                boolean isChildOrSelf = nextHeading.number.equals(anchorHeading.number)
                        || nextHeading.number.startsWith(anchorHeading.number + ".");
                if (!isChildOrSelf && nextHeading.level() <= anchorHeading.level()) {
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
            if (Objects.equals(chunks.get(i).getId(), targetId)) return i;
        }
        return -1;
    }

    private HeadingInfo firstHeading(DocumentChunk chunk) {
        String content = normalizeForSearch(chunk.getContent());
        Matcher matcher = HEADING_PATTERN.matcher(content);
        while (matcher.find()) {
            String number = matcher.group(1);
            String title = matcher.group(2).trim();
            if (!isLikelyHeadingTitle(title)) continue;
            return new HeadingInfo(number, title);
        }
        return null;
    }

    private boolean isLikelyHeadingTitle(String title) {
        String normalized = title == null ? "" : title.trim();
        if (normalized.length() < 4) return false;
        return !normalized.matches(".*\\b(phut|ngay|request|page|trang)\\b.*");
    }

    private List<DocumentChunk> expandAroundAnchors(List<DocumentChunk> anchors, UUID widgetId) {
        if (anchors == null || anchors.isEmpty()) return List.of();

        List<DocumentChunk> result = new ArrayList<>();
        for (DocumentChunk anchor : anchors) {
            int from = Math.max(0, anchor.getOrderIndex() - WINDOW_BEFORE);
            int to = anchor.getOrderIndex() + WINDOW_AFTER;
            result.addAll(documentChunkRepository
                    .findByWidgetConfigIdAndDocumentIdAndOrderIndexBetweenOrderByOrderIndexAsc(
                            widgetId, anchor.getDocument().getId(), from, to));
        }
        return result;
    }

    // ================================================================
    // SEARCH TERM EXTRACTION & LEXICAL SCORING
    // ================================================================

    private List<String> extractSearchTerms(String question) {
        if (question == null || question.isBlank()) return List.of();

        String normalizedQuestion = normalizeForSearch(question);
        Set<String> stopWords = Set.of(
                "gom", "nhung", "buoc", "nao",
                "liet", "ke", "cac", "va", "cua",
                "tung", "chinh",
                "trinh", "bay", "bao", "tom", "tat", "tong", "hop",
                "he", "thong", "tai", "lieu", "noi", "dung", "phan", "muc", "chuong",
                "hay", "vui", "long", "cho", "biet", "gi", "sao", "nhu", "the",
                "duoc", "voi", "trong", "len", "xuong", "khi", "neu"
        );

        return Arrays.stream(normalizedQuestion.split("[^\\p{L}\\p{N}]+"))
                .map(String::trim)
                .filter(term -> term.length() >= 3)
                .filter(term -> !stopWords.contains(term))
                .distinct()
                .toList();
    }

    private String normalizeForSearch(String value) {
        if (value == null || value.isBlank()) return "";
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace('đ', 'd')
                .replace('Đ', 'D')
                .toLowerCase(Locale.ROOT);
        return normalized.replaceAll("\\s+", " ").trim();
    }

    private int lexicalScore(DocumentChunk chunk, List<String> terms) {
        String content = normalizeForSearch(Optional.ofNullable(chunk.getContent()).orElse(""));
        String heading = normalizeForSearch(Optional.ofNullable(chunk.getHeadingPathText()).orElse(""));
        String sectionTitle = normalizeForSearch(Optional.ofNullable(chunk.getSectionTitle()).orElse(""));
        String haystack = (heading + " " + sectionTitle + " " + content).trim();

        int score = 0;
        for (String term : terms) {
            String t = normalizeForSearch(term);
            if (!t.isBlank() && haystack.contains(t)) score++;
        }

        // Bonus: section_summary và table_summary được ưu tiên cho expanded query
        String chunkType = chunk.getChunkType();
        if ("section_summary".equalsIgnoreCase(chunkType)
                || "table_summary".equalsIgnoreCase(chunkType)) {
            score += Math.min(score, 3);
        } else if ("text".equalsIgnoreCase(chunkType)) {
            score += Math.min(score, 2);
        }

        return score;
    }

    // ================================================================
    // DEDUP → SORT → BUDGET
    // ================================================================

    /**
     * Dedup → sắp xếp theo thứ tự tài liệu (sectionOrder ASC, orderIndex ASC) → cắt budget.
     *
     * Quan trọng: phải sắp xếp theo thứ tự tài liệu GỐC trước khi đưa cho LLM,
     * để LLM đọc context theo đúng trình tự logic, tránh hiểu nhầm do context lộn xộn.
     *
     * @param isLockedScope true nếu retrieval đã lock vào một section cụ thể → budget rộng hơn.
     */
    private List<RetrievedContext> dedupeSortBudget(List<DocumentChunk> chunks,
                                                     QueryAnalyzerService.QueryType queryType,
                                                     boolean isLockedScope,
                                                     String question) {
        boolean isExpandedQuery = isExpanded(queryType);

        int finalLimit;
        int maxContextChars;
        if (isLockedScope) {
            finalLimit = FINAL_LIMIT_LOCKED;
            maxContextChars = MAX_CONTEXT_CHARS_LOCKED;
        } else if (isExpandedQuery) {
            finalLimit = FINAL_LIMIT_EXPANDED;
            maxContextChars = MAX_CONTEXT_CHARS_EXPANDED;
        } else {
            finalLimit = FINAL_LIMIT;
            maxContextChars = MAX_CONTEXT_CHARS;
        }

        // Dedup theo chunk ID
        Map<UUID, DocumentChunk> unique = chunks.stream()
                .filter(c -> c.getId() != null)
                .collect(Collectors.toMap(
                        DocumentChunk::getId,
                        c -> c,
                        (a, b) -> a,
                        LinkedHashMap::new));

        // Sắp xếp theo thứ tự tài liệu gốc: (documentId, sectionOrder, orderIndex)
        List<DocumentChunk> sorted = unique.values().stream()
                .sorted(Comparator
                        .comparing((DocumentChunk c) -> c.getDocument() != null
                                ? c.getDocument().getId().toString() : "")
                        .thenComparingInt(c -> Optional.ofNullable(c.getSectionOrder()).orElse(0))
                        .thenComparingInt(c -> Optional.ofNullable(c.getOrderIndex()).orElse(0)))
                .toList();

        // section_summary và table_summary nên được đưa lên đầu trong expanded/locked query
        if (isExpandedQuery || isLockedScope) {
            sorted = prioritizeSummaryChunks(sorted);
        }

        List<RetrievedContext> result = new ArrayList<>();
        int totalChars = 0;

        for (DocumentChunk c : sorted) {
            if (result.size() >= finalLimit) {
                log.info("[RAG] Budget: finalLimit={} reached; {} chunks excluded by count limit",
                        finalLimit, sorted.size() - result.size());
                break;
            }

            String content = c.getContent() == null ? "" : c.getContent();
            if (totalChars + content.length() > maxContextChars) {
                log.info("[RAG] Budget: maxContextChars={} reached at chunk #{}, {} chunks excluded by char limit",
                        maxContextChars, result.size(), sorted.size() - result.size());
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

        log.info("[RAG] dedupeSortBudget: input={} unique={} output={} totalChars={} " +
                        "limit={}/{} locked={}",
                chunks.size(), unique.size(), result.size(), totalChars,
                finalLimit, maxContextChars, isLockedScope);

        return result;
    }

    // ================================================================
    // UTILITIES
    // ================================================================

    /**
     * Tìm sectionId root hợp lý để lock scope sau khi reranker xác định được top section.
     *
     * Chiến lược "leo 1 cấp":
     *   - "sec_2"   (level 1, root) → trả về "sec_2" (giữ nguyên)
     *   - "sec_2.1" (level 2, leaf) → kiểm tra "sec_2" có tồn tại không → trả về "sec_2"
     *   - "sec_2.1.3" (level 3)     → leo lên "sec_2.1" nếu tồn tại
     *
     * Không leo quá 1 cấp để tránh bring in quá nhiều context khi root section lớn.
     * Ví dụ: sec_6.3 → sec_6 (không leo vì sec_6 có 5 children sec_6.1..6.5 = quá nhiều).
     */
    private String findReasonableLockRoot(String sectionId, List<DocumentSection> allSections) {
        if (sectionId == null || !sectionId.startsWith("sec_")) return sectionId;

        String number = sectionId.substring("sec_".length());
        // Nếu đã là level 1 (không có dấu chấm) → giữ nguyên
        if (!number.contains(".")) return sectionId;

        // Leo lên 1 cấp: "sec_2.1" → "sec_2", "sec_2.1.3" → "sec_2.1"
        int lastDot = number.lastIndexOf('.');
        String parentNumber = number.substring(0, lastDot);
        String parentSectionId = "sec_" + parentNumber;

        boolean parentExists = allSections.stream()
                .anyMatch(s -> parentSectionId.equals(s.getSectionKey()));

        if (parentExists) {
            log.debug("[RAG] findReasonableLockRoot: '{}' → parent '{}' (exists)", sectionId, parentSectionId);
            return parentSectionId;
        }

        log.debug("[RAG] findReasonableLockRoot: '{}' → parent '{}' NOT found, using original", sectionId, parentSectionId);
        return sectionId;
    }

    private boolean isExpanded(QueryAnalyzerService.QueryType queryType) {
        return queryType == QueryAnalyzerService.QueryType.LIST_ALL
                || queryType == QueryAnalyzerService.QueryType.TABLE_LOOKUP
                || queryType == QueryAnalyzerService.QueryType.SECTION_SUMMARY
                || queryType == QueryAnalyzerService.QueryType.COUNT_QUERY;
    }

    private void extractIds(TextSegment seg,
                            Set<UUID> anchorChunkIds,
                            Set<String> sectionIds,
                            Set<String> tableIds,
                            Set<UUID> vectorDocumentIds) {
        String chunkId = seg.metadata().getString("chunk_id");
        if (chunkId != null && !chunkId.isBlank()) {
            try { anchorChunkIds.add(UUID.fromString(chunkId)); } catch (IllegalArgumentException ignored) {}
        }
        String sectionId = seg.metadata().getString("section_id");
        if (sectionId != null && !sectionId.isBlank()) sectionIds.add(sectionId);

        String tableId = seg.metadata().getString("table_id");
        if (tableId != null && !tableId.isBlank()) tableIds.add(tableId);

        String documentId = seg.metadata().getString("document_id");
        if (documentId != null && !documentId.isBlank()) {
            try { vectorDocumentIds.add(UUID.fromString(documentId)); }
            catch (IllegalArgumentException ignored) {}
        }
    }

    private Set<String> deriveParentIds(Set<String> sectionIds) {
        Set<String> parentIds = new LinkedHashSet<>();
        for (String sectionId : sectionIds) {
            if (sectionId == null || !sectionId.startsWith("sec_")) continue;
            String number = sectionId.substring("sec_".length());
            if (number.startsWith("idx_")) continue;
            int lastDot = number.lastIndexOf('.');
            if (lastDot > 0) {
                String parentNumber = number.substring(0, lastDot);
                parentIds.add("parent_" + parentNumber);
            }
        }
        return parentIds;
    }

    /**
     * Đặt section_summary và table_summary lên đầu danh sách,
     * theo sau bởi các chunks còn lại theo thứ tự tài liệu.
     * Giúp LLM có context tổng quan trước khi đọc detail chunks.
     */
    private List<DocumentChunk> prioritizeSummaryChunks(List<DocumentChunk> sorted) {
        List<DocumentChunk> summaries = sorted.stream()
                .filter(c -> "section_summary".equals(c.getChunkType())
                        || "table_summary".equals(c.getChunkType())
                        || "parent_section_summary".equals(c.getChunkType()))
                .toList();
        List<DocumentChunk> others = sorted.stream()
                .filter(c -> !"section_summary".equals(c.getChunkType())
                        && !"table_summary".equals(c.getChunkType())
                        && !"parent_section_summary".equals(c.getChunkType()))
                .toList();

        List<DocumentChunk> result = new ArrayList<>(summaries);
        result.addAll(others);
        return result;
    }

    // ================================================================
    // TOP-K (per-request vector search limit)
    // ================================================================

    public static int normalizeAnchorTopK(Integer requestedTopK) {
        if (requestedTopK == null) {
            return DEFAULT_ANCHOR_TOP_K;
        }
        return Math.max(MIN_ANCHOR_TOP_K, Math.min(MAX_ANCHOR_TOP_K, requestedTopK));
    }

    /**
     * Reads {@code topK} from playground override map (Number or String).
     */
    public static Integer parseTopKOverride(Map<String, Object> params) {
        if (params == null || params.isEmpty()) {
            return null;
        }
        Object raw = params.get("topK");
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number number) {
            return number.intValue();
        }
        if (raw instanceof String text) {
            String trimmed = text.trim();
            if (trimmed.isEmpty()) {
                return null;
            }
            try {
                return Integer.parseInt(trimmed);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    // ================================================================
    // INNER RECORDS
    // ================================================================

    private record HeadingInfo(String number, String title) {
        int level() { return number.split("\\.").length; }
    }

    private record SectionRange(int startIndex, int endIndex) {}
}
