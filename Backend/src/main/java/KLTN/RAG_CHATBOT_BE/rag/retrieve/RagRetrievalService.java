package KLTN.RAG_CHATBOT_BE.rag.retrieve;

import KLTN.RAG_CHATBOT_BE.domain.document.DocumentChunk;
import KLTN.RAG_CHATBOT_BE.domain.document.DocumentChunkRepository;
import KLTN.RAG_CHATBOT_BE.domain.document.DocumentSection;
import KLTN.RAG_CHATBOT_BE.domain.document.DocumentSectionRepository;
import KLTN.RAG_CHATBOT_BE.dto.RetrievedContext;
import KLTN.RAG_CHATBOT_BE.index.embedding.EmbeddingService;
import KLTN.RAG_CHATBOT_BE.ingest.normalize.NormalizedTableService;
import KLTN.RAG_CHATBOT_BE.rag.budget.PromptBudgetResolver;
import KLTN.RAG_CHATBOT_BE.rag.analysis.QueryAnalyzerService;
import KLTN.RAG_CHATBOT_BE.rag.analysis.QuerySignalExtractor;
import KLTN.RAG_CHATBOT_BE.audit.metrics.RagLatencyTrace;
import KLTN.RAG_CHATBOT_BE.rag.rerank.RerankService;
import dev.langchain4j.data.segment.TextSegment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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

    /** Fixed Qdrant vector anchor limit — not controlled by UI topK. */
    public static final int DEFAULT_VECTOR_ANCHOR_K = 30;

    /** Default final contexts after rerank when request/modelConfig omit topK. */
    public static final int DEFAULT_FINAL_CONTEXT_TOP_N = 10;

    public static final int MIN_FINAL_CONTEXT_TOP_N = 1;

    public static final int MAX_FINAL_CONTEXT_TOP_N = 30;

    /** @deprecated use {@link #DEFAULT_VECTOR_ANCHOR_K} */
    @Deprecated
    public static final int DEFAULT_ANCHOR_TOP_K = DEFAULT_VECTOR_ANCHOR_K;

    /** @deprecated use {@link #MIN_FINAL_CONTEXT_TOP_N} */
    @Deprecated
    public static final int MIN_ANCHOR_TOP_K = MIN_FINAL_CONTEXT_TOP_N;

    /** @deprecated use {@link #MAX_FINAL_CONTEXT_TOP_N} */
    @Deprecated
    public static final int MAX_ANCHOR_TOP_K = MAX_FINAL_CONTEXT_TOP_N;

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
    private static final int TABLE_QUERY_SCORING_CANDIDATE_LIMIT = 240;
    private static final int FACT_SCORING_BUDGET = 80;
    private static final int MULTI_ATTRIBUTE_SCORING_BUDGET = 220;
    private static final int COMPARE_SCORING_BUDGET = 160;
    private static final int LIST_SCORING_BUDGET = 220;
    private static final int WEAK_SCORING_BUDGET = 60;
    private static final int SAFETY_TAIL_CANDIDATES = 40;

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
    private final KeywordSearchService keywordSearchService;
    private final PromptBudgetResolver promptBudgetResolver;

    @Value("${rag.retrieval.vector-anchor-k:30}")
    private int configuredVectorAnchorK;

    @Value("${rag.retrieval.hybrid.vector-weight:0.35}")
    private double hybridVectorWeight;

    @Value("${rag.retrieval.hybrid.keyword-weight:0.35}")
    private double hybridKeywordWeight;

    @Value("${rag.retrieval.hybrid.rerank-weight:0.30}")
    private double hybridRerankWeight;

    public record ScoredChunk(DocumentChunk chunk, double finalScore) {}

    record CheapScoredCandidate(DocumentChunk chunk, double score, boolean highConfidence) {}

    record ScoringBudget(int expensiveLimit, int safetyTail, String reason) {}

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
     * Full entry point with optional per-request final context top-N (UI field {@code topK}).
     *
     * @param finalContextTopNOverride số context cuối sau rerank; null → query-type default
     */
    public RetrievalResult retrieveWithMetadata(String question, UUID widgetId, Integer finalContextTopNOverride) {
        int fixedVectorAnchorK = fixedVectorAnchorK();
        log.info("[RAG][anchor] fixedVectorAnchorK={}", fixedVectorAnchorK);

        // ── STEP 0: Intent detection ───────────────────────────────────
        long analyzeStart = RagLatencyTrace.now();
        QueryAnalyzerService.QueryType queryType = queryAnalyzerService.analyze(question, widgetId);
        RagLatencyTrace trace = RagLatencyTrace.current();
        if (trace != null) {
            trace.addQueryAnalyzeMs(RagLatencyTrace.elapsedMs(analyzeStart));
        }
        int requestedFinalContextTopN = normalizeFinalContextTopN(finalContextTopNOverride);
        int finalContextTopN = resolveAdaptiveFinalContextTopN(question, finalContextTopNOverride, queryType);
        if (trace != null) {
            trace.setTopN(requestedFinalContextTopN, finalContextTopN);
        }
        String topNSource = finalContextTopNOverride != null ? "REQUEST|MODEL_CONFIG" : "DEFAULT";
        log.info("[RAG][topN] requestedTopN={} effectiveTopN={} reason={} source={}",
                requestedFinalContextTopN, finalContextTopN,
                adaptiveTopNReason(question, queryType), topNSource);
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
                anchors = embeddingService.search(variant, fixedVectorAnchorK, widgetId);
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

        // ── STEP 3b: Generic keyword search (parallel branch, supplements vector) ──
        KeywordSearchService.KeywordSearchResult keywordResult = null;
        Map<UUID, Double> keywordScoresByChunkId = Map.of();
        Set<UUID> keywordChunkIds = new LinkedHashSet<>();
        if (keywordSearchService.isHybridEnabled()) {
            long keywordStart = RagLatencyTrace.now();
            keywordResult = keywordSearchService.search(question, widgetId);
            if (trace != null) {
                trace.addKeywordMs(RagLatencyTrace.elapsedMs(keywordStart));
            }
            keywordScoresByChunkId = keywordResult.normalizedScoresByChunkId();
            keywordChunkIds = keywordResult.chunkIds();
            log.info("[RAG][hybrid] strategy=VECTOR+KEYWORD vectorCandidates={} keywordCandidates={}",
                    anchorChunkIds.size(), keywordChunkIds.size());
        }

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
                        "will be excluded by final context selection scope filter.",
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

        // ── STEP 5: Rerank-guided scope lock (preliminary scoring, không cắt final top-N) ──
        boolean isLockedScope = !lockedSectionIds.isEmpty();
        List<DocumentChunk> dedupedForLock = dedupeCandidates(expanded);
        QuerySignalExtractor.QuerySignals scopeSignals = keywordResult != null
                ? keywordResult.signals()
                : QuerySignalExtractor.extract(question);
        boolean allowRerankScopeLock = queryType != QueryAnalyzerService.QueryType.LIST_ALL
                && queryType != QueryAnalyzerService.QueryType.COUNT_QUERY
                && queryType != QueryAnalyzerService.QueryType.TABLE_LOOKUP
                && !KeywordSearchService.isTableLikeQuery(scopeSignals, question);
        if (allowRerankScopeLock && !isLockedScope && rerankService.isEnabled() && !dedupedForLock.isEmpty()) {
            List<ScoredChunk> preliminaryScores = scoreCandidatesForSelection(
                    question, dedupedForLock, queryType, anchorChunkIds, "RERANK_SERVICE", keywordScoresByChunkId);
            if (!preliminaryScores.isEmpty()) {
                double maxScore = preliminaryScores.get(0).finalScore();
                if (maxScore < RerankService.LOW_CONFIDENCE_THRESHOLD) {
                    log.warn("[RAG] LOW CONFIDENCE: max score={} < threshold={} cho query='{}'.",
                            String.format("%.4f", maxScore),
                            RerankService.LOW_CONFIDENCE_THRESHOLD, question);
                }

                if (allowRerankScopeLock && maxScore >= RERANK_LOCK_THRESHOLD) {
                    DocumentChunk topChunk = preliminaryScores.get(0).chunk();
                    String topSectionId = topChunk.getSectionId();

                    if (topSectionId != null && !topSectionId.isBlank()) {
                        String lockRootId = findReasonableLockRoot(topSectionId, allSections);
                        Set<String> rerankScope = expandDescendantSectionIds(lockRootId, allSections);

                        List<DocumentChunk> rerankScopeChunks = documentChunkRepository
                                .findByWidgetConfigIdAndSectionIdInOrderByDocumentIdAscOrderIndexAsc(
                                        widgetId, rerankScope);

                        if (!rerankScopeChunks.isEmpty()) {
                            log.info("[RAG] Rerank-Guided Lock ACTIVATED: score={} topSection='{}' " +
                                    "→ lockRoot='{}' scope={} ({} chunks) — replaced {} chunk pool",
                                    String.format("%.4f", maxScore),
                                    topSectionId, lockRootId, rerankScope,
                                    rerankScopeChunks.size(), expanded.size());
                            expanded = rerankScopeChunks;
                            isLockedScope = true;
                            lockedSectionLabel = "rerank-lock: '" + lockRootId + "'";
                        }
                    }
                }
            }
        }

        // Inject keyword-only candidates before merge/score (supplements vector pool)
        if (!keywordChunkIds.isEmpty()) {
            List<DocumentChunk> keywordChunks = documentChunkRepository
                    .findByWidgetConfigIdAndIdIn(widgetId, keywordChunkIds);
            expanded.addAll(keywordChunks);
        }

        List<DocumentChunk> dedupedPreScore = dedupeCandidates(expanded);
        List<DocumentChunk> boundedPreScore = boundCandidatesBeforeScoring(
                dedupedPreScore, question, anchorChunkIds, keywordScoresByChunkId, finalContextTopN);
        if (boundedPreScore.size() < dedupedPreScore.size()) {
            log.info("[RAG][candidates] boundedForScoring={} from={} tableQuery=true",
                    boundedPreScore.size(), dedupedPreScore.size());
            expanded = boundedPreScore;
            dedupedPreScore = boundedPreScore;
        }
        int bothSourceCount = 0;
        long mergeStart = RagLatencyTrace.now();
        if (keywordSearchService.isHybridEnabled() && keywordResult != null) {
            List<KeywordSearchService.MergedCandidate> mergedPreview = KeywordSearchService.mergeCandidates(
                    dedupedPreScore, anchorChunkIds, keywordResult);
            bothSourceCount = KeywordSearchService.countBothSource(mergedPreview);
            log.info("[RAG][hybrid] mergedCandidates={} dedupedCandidates={} bothSourceCount={}",
                    expanded.size(), dedupedPreScore.size(), bothSourceCount);
        }
        if (trace != null) {
            trace.addMergeMs(RagLatencyTrace.elapsedMs(mergeStart));
            trace.setCandidates(dedupedPreScore.size(), dedupedPreScore.size());
            trace.setMergeCandidateStats(
                    anchorChunkIds.size(),
                    keywordChunkIds.size(),
                    expanded.size(),
                    dedupedPreScore.size());
        }

        log.info("[RAG][candidates] vectorAnchors={} keywordAnchors={} afterExpansion={}",
                anchorChunkIds.size(), keywordChunkIds.size(), expanded.size());

        // ── STEP 6: Dedupe → score all → select top-N by score → document order ──
        List<RetrievedContext> result = selectFinalContexts(
                expanded, question, queryType, isLockedScope, finalContextTopN,
                anchorChunkIds, keywordScoresByChunkId);

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

    private List<DocumentChunk> boundCandidatesBeforeScoring(
            List<DocumentChunk> candidates,
            String question,
            Set<UUID> anchorChunkIds,
            Map<UUID, Double> keywordScoresByChunkId,
            int finalContextTopN
    ) {
        if (candidates == null || candidates.size() <= TABLE_QUERY_SCORING_CANDIDATE_LIMIT) {
            return candidates == null ? List.of() : candidates;
        }
        QuerySignalExtractor.QuerySignals signals = QuerySignalExtractor.extract(question);
        if (!KeywordSearchService.isTableLikeQuery(signals, question)) {
            return candidates;
        }
        if (!signals.identifiers().isEmpty() || !signals.dates().isEmpty()) {
            return candidates;
        }
        int limit = Math.max(TABLE_QUERY_SCORING_CANDIDATE_LIMIT, finalContextTopN * 12);
        List<ScoredChunk> ranked = new ArrayList<>();
        for (DocumentChunk chunk : candidates) {
            CheapScoredCandidate cheap = cheapPreScoreCandidate(
                    chunk, signals, anchorChunkIds, keywordScoresByChunkId);
            ranked.add(new ScoredChunk(chunk, cheap.score()));
        }
        return ranked.stream()
                .sorted(Comparator.comparingDouble(ScoredChunk::finalScore).reversed())
                .limit(limit)
                .map(ScoredChunk::chunk)
                .toList();
    }

    public static int resolveAdaptiveFinalContextTopN(
            String question,
            Integer override,
            QueryAnalyzerService.QueryType queryType
    ) {
        int requested = resolveFinalContextTopN(override, queryType);
        int adaptive = switch (adaptiveTopNReason(question, queryType)) {
            case "list_like" -> 15;
            case "multi_attribute_lookup" -> requested;
            case "compare_like" -> 10;
            case "fact_like" -> 7;
            case "oos_or_weak" -> 5;
            default -> requested;
        };
        return Math.max(MIN_FINAL_CONTEXT_TOP_N, Math.min(requested, adaptive));
    }

    static String adaptiveTopNReason(String question, QueryAnalyzerService.QueryType queryType) {
        QuerySignalExtractor.QuerySignals signals = QuerySignalExtractor.extract(question == null ? "" : question);
        String normalized = QuerySignalExtractor.normalize(question == null ? "" : question);
        if (queryType == QueryAnalyzerService.QueryType.LIST_ALL
                || queryType == QueryAnalyzerService.QueryType.COUNT_QUERY
                || containsAnyNormalized(normalized, "liet ke", "tat ca", "danh sach", "bao gom")) {
            return "list_like";
        }
        if (isMultiAttributeLookup(normalized, signals, queryType)) {
            return "multi_attribute_lookup";
        }
        if (containsAnyNormalized(normalized, "so sanh", "compare", "khac nhau", "giong nhau")
                || (CellAwareTableRowScorer.isCompareQuery(question, signals)
                && containsAnyNormalized(normalized, " va ", " voi ", " and ", " vs "))) {
            return "compare_like";
        }
        if (queryType == QueryAnalyzerService.QueryType.NORMAL_FACT
                || queryType == QueryAnalyzerService.QueryType.TABLE_LOOKUP
                || !signals.identifiers().isEmpty()
                || !signals.dates().isEmpty()
                || signals.structuredLabels().size() == 1) {
            return "fact_like";
        }
        if (question == null || question.isBlank()) {
            return "oos_or_weak";
        }
        return "default";
    }

    private static boolean isMultiAttributeLookup(
            String normalized,
            QuerySignalExtractor.QuerySignals signals,
            QueryAnalyzerService.QueryType queryType
    ) {
        if (normalized == null || normalized.isBlank()) {
            return false;
        }
        boolean rowLike = queryType == QueryAnalyzerService.QueryType.TABLE_LOOKUP
                || (signals != null && (!signals.identifiers().isEmpty()
                || !signals.structuredLabels().isEmpty()
                || !signals.numbers().isEmpty()));
        if (!rowLike) {
            return false;
        }
        long separators = normalized.chars().filter(ch -> ch == ',' || ch == ';').count();
        int questionMarkers = 0;
        for (String marker : List.of(" nao", " may", " gi", " dau", " khi nao", " which", " what", " where", " when")) {
            if (normalized.contains(marker)) {
                questionMarkers++;
            }
        }
        return separators >= 2
                || questionMarkers >= 3
                || (queryType == QueryAnalyzerService.QueryType.TABLE_LOOKUP
                && signals != null
                && signals.structuredLabels().size() >= 2);
    }

    private static boolean containsAnyNormalized(String normalized, String... needles) {
        if (normalized == null || normalized.isBlank()) {
            return false;
        }
        for (String needle : needles) {
            if (normalized.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static CheapScoredCandidate cheapPreScoreCandidate(
            DocumentChunk chunk,
            QuerySignalExtractor.QuerySignals signals,
            Set<UUID> anchorChunkIds,
            Map<UUID, Double> keywordScoresByChunkId
    ) {
        double score = 0.0;
        boolean vector = chunk != null && chunk.getId() != null
                && anchorChunkIds != null && anchorChunkIds.contains(chunk.getId());
        double keyword = chunk != null && chunk.getId() != null && keywordScoresByChunkId != null
                ? keywordScoresByChunkId.getOrDefault(chunk.getId(), 0.0) : 0.0;
        if (vector) {
            score += 8.0;
        }
        if (keyword > 0) {
            score += keyword * 8.0;
        }
        if (vector && keyword > 0) {
            score += 3.0;
        }
        String type = chunk == null || chunk.getChunkType() == null ? "text" : chunk.getChunkType();
        if ("normalized_table_row".equals(type)) {
            score += 2.0;
            if (chunk.getCellsJson() != null && !chunk.getCellsJson().isBlank()) {
                score += 1.0;
            }
        } else if ("table_summary".equals(type)) {
            score += 0.5;
        }
        double overlap = quickTermOverlapScore(chunk, signals);
        score += overlap;
        boolean highConfidence = "normalized_table_row".equals(type)
                && hasExactSignalsInNormalizedText(chunk, signals)
                && (overlap >= 5.0 || (vector && keyword > 0) || keyword >= 0.75);
        if (highConfidence && signals != null && !signals.identifiers().isEmpty()) {
            score += Math.max(0.0, 6.0 - identifierNoise(chunk, signals) * 2.0);
        }
        return new CheapScoredCandidate(chunk, score, highConfidence);
    }

    private static boolean hasExactSignalsInNormalizedText(
            DocumentChunk chunk,
            QuerySignalExtractor.QuerySignals signals
    ) {
        if (chunk == null || signals == null) {
            return false;
        }
        String haystack = KeywordSearchService.haystack(chunk);
        int matchedCategories = 0;
        boolean matchedIdentifier = false;
        for (String id : signals.identifiers()) {
            if (haystack.contains(QuerySignalExtractor.normalize(id))) {
                matchedIdentifier = true;
                matchedCategories++;
                break;
            }
        }
        for (String date : signals.dates()) {
            if (haystack.contains(QuerySignalExtractor.normalize(date))) {
                matchedCategories++;
                break;
            }
        }
        for (String label : signals.structuredLabels()) {
            if (haystack.contains(QuerySignalExtractor.normalize(label))) {
                matchedCategories++;
                break;
            }
        }
        for (String ngram : signals.ngrams()) {
            if (ngram.length() >= 4 && haystack.contains(ngram)) {
                matchedCategories++;
                break;
            }
        }
        return matchedIdentifier || matchedCategories >= 2;
    }

    private static double quickTermOverlapScore(DocumentChunk chunk, QuerySignalExtractor.QuerySignals signals) {
        if (chunk == null || signals == null) {
            return 0.0;
        }
        String haystack = KeywordSearchService.haystack(chunk);
        double score = 0.0;
        for (String id : signals.identifiers()) {
            if (haystack.contains(QuerySignalExtractor.normalize(id))) {
                score += 3.0;
            }
        }
        for (String label : signals.structuredLabels()) {
            if (haystack.contains(QuerySignalExtractor.normalize(label))) {
                score += 2.0;
            }
        }
        for (String ngram : signals.ngrams()) {
            if (haystack.contains(ngram)) {
                score += 0.2;
            }
        }
        return score;
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
        } else if ("normalized_table_row".equalsIgnoreCase(chunkType)) {
            score += Math.min(score, 4);
        } else if ("text".equalsIgnoreCase(chunkType)) {
            score += Math.min(score, 2);
        }

        return score;
    }

    // ================================================================
    // DEDUP → SCORE → SELECT TOP-N → DOCUMENT ORDER
    // ================================================================

    private List<RetrievedContext> selectFinalContexts(List<DocumentChunk> chunks,
                                                       String question,
                                                       QueryAnalyzerService.QueryType queryType,
                                                       boolean isLockedScope,
                                                       int finalContextTopN,
                                                       Set<UUID> anchorChunkIds,
                                                       Map<UUID, Double> keywordScoresByChunkId) {
        boolean isExpandedQuery = isExpanded(queryType);
        int maxContextChars = resolveMaxContextChars(queryType, isLockedScope);
        if (promptBudgetResolver.isEnabled()) {
            int budget = promptBudgetResolver.resolveCharBudget(queryType, isLockedScope);
            if (budget > 0) {
                maxContextChars = Math.min(maxContextChars, budget);
                maxContextChars = Math.min(maxContextChars, promptBudgetResolver.hardMax());
            }
        }

        List<DocumentChunk> deduped = dedupeCandidates(chunks);
        log.info("[RAG][candidates] deduped={}", deduped.size());

        long scoringStart = RagLatencyTrace.now();
        List<ScoredChunk> scored = scoreCandidatesForSelection(
                question, deduped, queryType, anchorChunkIds, null, keywordScoresByChunkId);
        scored = demoteLeakyTextCandidates(question, scored);
        RagLatencyTrace trace = RagLatencyTrace.current();
        if (trace != null) {
            trace.addScoringMs(RagLatencyTrace.elapsedMs(scoringStart));
            trace.setCandidates(deduped.size(), scored.size());
        }
        logHybridTopRanks(scored, anchorChunkIds, keywordScoresByChunkId);

        long selectStart = RagLatencyTrace.now();
        SelectionWithBudget selection = selectTopNByScoreWithBudget(
                scored, finalContextTopN, maxContextChars, question);
        if (trace != null) {
            trace.addContextSelectMs(RagLatencyTrace.elapsedMs(selectStart));
        }
        logCellAwareNormalizedRows(question, scored);
        List<DocumentChunk> ordered = sortByDocumentOrder(
                selection.chunks(), isExpandedQuery || isLockedScope);

        int contextChars = ordered.stream()
                .mapToInt(c -> c.getContent() == null ? 0 : c.getContent().length())
                .sum();
        promptBudgetResolver.logBudget(
                finalContextTopN, ordered.size(), contextChars, selection.budgetLimited());
        if (trace != null) {
            trace.setContextStats(ordered.size(), contextChars);
        }

        log.info("[RAG][select] selectedByScore={} finalContexts={} maxContextChars={}",
                selection.chunks().size(), ordered.size(), maxContextChars);
        log.info("[RAG][prompt-order] sortedByDocumentOrder=true");

        return ordered.stream().map(this::toRetrievedContext).toList();
    }

    record SelectionWithBudget(List<DocumentChunk> chunks, boolean budgetLimited) {}

    private void logHybridTopRanks(List<ScoredChunk> scored,
                                   Set<UUID> anchorChunkIds,
                                   Map<UUID, Double> keywordScoresByChunkId) {
        if (!keywordSearchService.isHybridEnabled() || scored == null || scored.isEmpty()) {
            return;
        }
        int limit = Math.min(5, scored.size());
        for (int i = 0; i < limit; i++) {
            ScoredChunk sc = scored.get(i);
            DocumentChunk c = sc.chunk();
            if (c.getId() == null) {
                continue;
            }
            boolean fromVector = anchorChunkIds != null && anchorChunkIds.contains(c.getId());
            boolean fromKeyword = keywordScoresByChunkId != null
                    && keywordScoresByChunkId.containsKey(c.getId());
            String source = fromVector && fromKeyword ? "BOTH"
                    : fromVector ? "VECTOR" : fromKeyword ? "KEYWORD" : "EXPANSION";
            double kScore = keywordScoresByChunkId != null
                    ? keywordScoresByChunkId.getOrDefault(c.getId(), 0.0) : 0.0;
            double vScore = fromVector ? 1.0 : 0.0;
            log.info("[RAG][hybrid-top] rank={} source={} chunkType={} vScore={} kScore={} rScore={}",
                    i + 1, source, c.getChunkType(),
                    String.format("%.3f", vScore),
                    String.format("%.3f", kScore),
                    String.format("%.4f", sc.finalScore()));
        }
    }

    static List<DocumentChunk> dedupeCandidates(List<DocumentChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return List.of();
        }
        Map<UUID, DocumentChunk> unique = chunks.stream()
                .filter(c -> c.getId() != null)
                .collect(Collectors.toMap(
                        DocumentChunk::getId,
                        c -> c,
                        (a, b) -> a,
                        LinkedHashMap::new));
        return new ArrayList<>(unique.values());
    }

    private List<ScoredChunk> scoreCandidatesForSelection(String question,
                                                          List<DocumentChunk> deduped,
                                                          QueryAnalyzerService.QueryType queryType,
                                                          Set<UUID> anchorChunkIds,
                                                          String scorerHint,
                                                          Map<UUID, Double> keywordScoresByChunkId) {
        if (deduped.isEmpty()) {
            return List.of();
        }

        QuerySignalExtractor.QuerySignals signals = QuerySignalExtractor.extract(question);
        List<CheapScoredCandidate> cheapScored = cheapPreScoreCandidates(
                deduped, signals, anchorChunkIds, keywordScoresByChunkId);
        ScoringBudget budget = resolveScoringBudget(question, signals, queryType, deduped.size());
        List<DocumentChunk> expensiveSubset = selectExpensiveScoringSubset(
                cheapScored, budget, keywordScoresByChunkId);
        int droppedByCheapGate = Math.max(0, deduped.size() - expensiveSubset.size());
        long cellAwareStart = RagLatencyTrace.now();
        Map<UUID, Double> rerankScores = new HashMap<>();
        String scorer = scorerHint;
        if (rerankService.isEnabled()) {
            List<RerankService.ScoredChunk> reranked = rerankService.scoreCandidates(question, expensiveSubset);
            for (RerankService.ScoredChunk rc : reranked) {
                if (rc.chunk().getId() != null) {
                    rerankScores.put(rc.chunk().getId(), rc.score());
                }
            }
            if (!rerankScores.isEmpty()) {
                scorer = "RERANK_SERVICE";
            }
        }

        Map<String, Double> idfByTerm = computeIdfWeights(expensiveSubset, extractQueryTermsForScoring(question));
        double maxLexical = expensiveSubset.stream()
                .mapToDouble(c -> lexicalIdfScore(c, idfByTerm))
                .max()
                .orElse(0.0);
        boolean hybrid = keywordSearchService.isHybridEnabled()
                && keywordScoresByChunkId != null && !keywordScoresByChunkId.isEmpty();
        List<ScoredChunk> scored = new ArrayList<>();
        for (DocumentChunk chunk : expensiveSubset) {
            double lexicalRaw = lexicalIdfScore(chunk, idfByTerm);
            double lexical = maxLexical > 0 ? lexicalRaw / maxLexical : 0.0;
            double vector = anchorChunkIds != null && chunk.getId() != null
                    && anchorChunkIds.contains(chunk.getId()) ? 1.0 : 0.0;
            double keyword = chunk.getId() != null && keywordScoresByChunkId != null
                    ? keywordScoresByChunkId.getOrDefault(chunk.getId(), 0.0) : 0.0;
            double rerank = rerankScores.getOrDefault(chunk.getId(), -1.0);
            double finalScore;
            if (hybrid) {
                if (rerank >= 0.0) {
                    finalScore = hybridVectorWeight * vector
                            + hybridKeywordWeight * keyword
                            + hybridRerankWeight * rerank;
                } else {
                    double lexicalWeight = 1.0 - hybridVectorWeight - hybridKeywordWeight;
                    finalScore = hybridVectorWeight * vector
                            + hybridKeywordWeight * keyword
                            + Math.max(0.0, lexicalWeight) * lexical;
                }
            } else if (rerank >= 0.0) {
                finalScore = 0.6 * rerank + 0.3 * lexical + 0.1 * vector;
            } else {
                finalScore = 0.7 * lexical + 0.3 * vector;
            }
            scored.add(new ScoredChunk(chunk, finalScore));
        }

        scored = applyCellAwareScoreBoost(question, scored);
        scored = applyTableRowPriorityAdjustments(question, scored);
        RagLatencyTrace trace = RagLatencyTrace.current();
        if (trace != null) {
            long cellAwareMs = RagLatencyTrace.elapsedMs(cellAwareStart);
            int cellAwareCandidates = (int) expensiveSubset.stream()
                    .filter(c -> "normalized_table_row".equals(c.getChunkType()))
                    .count();
            trace.addCellAwareMs(cellAwareMs);
            trace.setScoringCandidateStats(
                    cheapScored.size(),
                    cellAwareCandidates,
                    rerankService.isEnabled() ? expensiveSubset.size() : 0,
                    droppedByCheapGate);
        }

        if (scorer == null) {
            scorer = hybrid
                    ? "HYBRID|RERANK|BM25_IDF"
                    : rerankScores.isEmpty() ? "BM25_IDF|VECTOR_FALLBACK" : "RERANK_SERVICE|BM25_IDF";
        }
        long sortStart = RagLatencyTrace.now();
        scored = scored.stream()
                .sorted(Comparator.comparingDouble(ScoredChunk::finalScore).reversed())
                .toList();
        if (trace != null) {
            trace.addFinalSortMs(RagLatencyTrace.elapsedMs(sortStart));
        }
        log.info("[RAG][rerank] scorer={} candidates={} cheapScored={} cellAwareCandidates={} "
                        + "budget={} reason={} droppedByCheapGate={} hybrid={}",
                scorer, scored.size(), cheapScored.size(),
                expensiveSubset.stream().filter(c -> "normalized_table_row".equals(c.getChunkType())).count(),
                budget.expensiveLimit(), budget.reason(), droppedByCheapGate, hybrid);
        return scored;
    }

    static List<CheapScoredCandidate> cheapPreScoreCandidates(
            List<DocumentChunk> deduped,
            QuerySignalExtractor.QuerySignals signals,
            Set<UUID> anchorChunkIds,
            Map<UUID, Double> keywordScoresByChunkId
    ) {
        if (deduped == null || deduped.isEmpty()) {
            return List.of();
        }
        return deduped.stream()
                .map(c -> cheapPreScoreCandidate(c, signals, anchorChunkIds, keywordScoresByChunkId))
                .sorted(Comparator.comparingDouble(CheapScoredCandidate::score).reversed())
                .toList();
    }

    static ScoringBudget resolveScoringBudget(
            String question,
            QuerySignalExtractor.QuerySignals signals,
            int candidateCount
    ) {
        return resolveScoringBudget(question, signals, null, candidateCount);
    }

    static ScoringBudget resolveScoringBudget(
            String question,
            QuerySignalExtractor.QuerySignals signals,
            QueryAnalyzerService.QueryType queryType,
            int candidateCount
    ) {
        QueryAnalyzerService.QueryType assumedType = KeywordSearchService.isTableLikeQuery(signals, question)
                ? QueryAnalyzerService.QueryType.TABLE_LOOKUP
                : QueryAnalyzerService.QueryType.NORMAL_FACT;
        QueryAnalyzerService.QueryType effectiveType = queryType == null ? assumedType : queryType;
        String reason = adaptiveTopNReason(question, effectiveType);
        String normalizedQuestion = QuerySignalExtractor.normalize(question);
        boolean listMarkers = containsAnyNormalized(normalizedQuestion,
                "liet ke", "tat ca", "danh sach", "bao gom", "nhung gi", "list all", "all entries",
                "co nhung", "co cac");
        if (question == null || question.isBlank()) {
            reason = "oos_or_weak";
        } else if ((effectiveType == QueryAnalyzerService.QueryType.LIST_ALL
                || effectiveType == QueryAnalyzerService.QueryType.COUNT_QUERY)
                && (signals == null || signals.identifiers().isEmpty() || listMarkers)) {
            reason = "list_like";
        }
        if (containsAnyNormalized(normalizedQuestion,
                "so sanh", "compare", "khac nhau", "giong nhau", " vs ")) {
            reason = "compare_like";
        } else if ((signals == null || signals.identifiers().isEmpty() || listMarkers) && listMarkers) {
            reason = "list_like";
        }
        if ("list_like".equals(reason)
                && signals != null
                && !signals.identifiers().isEmpty()
                && !listMarkers) {
            reason = signals.structuredLabels().size() >= 2 ? "multi_attribute_lookup" : "fact_like";
        }
        int limit = switch (reason) {
            case "list_like" -> LIST_SCORING_BUDGET;
            case "compare_like" -> COMPARE_SCORING_BUDGET;
            case "multi_attribute_lookup" -> MULTI_ATTRIBUTE_SCORING_BUDGET;
            case "oos_or_weak" -> WEAK_SCORING_BUDGET;
            default -> FACT_SCORING_BUDGET;
        };
        return new ScoringBudget(Math.min(Math.max(1, candidateCount), limit), SAFETY_TAIL_CANDIDATES, reason);
    }

    static List<DocumentChunk> selectExpensiveScoringSubset(
            List<CheapScoredCandidate> cheapScored,
            ScoringBudget budget,
            Map<UUID, Double> keywordScoresByChunkId
    ) {
        if (cheapScored == null || cheapScored.isEmpty()) {
            return List.of();
        }
        int limit = budget == null ? FACT_SCORING_BUDGET : budget.expensiveLimit();
        List<CheapScoredCandidate> highConfidence = cheapScored.stream()
                .filter(CheapScoredCandidate::highConfidence)
                .limit(Math.max(0, limit - (budget == null ? SAFETY_TAIL_CANDIDATES : budget.safetyTail())))
                .toList();
        List<DocumentChunk> selected = new ArrayList<>();
        Set<UUID> seen = new LinkedHashSet<>();
        for (CheapScoredCandidate candidate : highConfidence) {
            addIfNew(selected, seen, candidate.chunk());
        }
        for (CheapScoredCandidate candidate : cheapScored) {
            if (selected.size() >= limit) {
                break;
            }
            addIfNew(selected, seen, candidate.chunk());
        }
        if (keywordScoresByChunkId != null && !keywordScoresByChunkId.isEmpty()) {
            for (CheapScoredCandidate candidate : cheapScored) {
                if (selected.size() >= limit) {
                    break;
                }
                DocumentChunk c = candidate.chunk();
                if (c != null && c.getId() != null && keywordScoresByChunkId.containsKey(c.getId())) {
                    addIfNew(selected, seen, c);
                }
            }
        }
        return selected;
    }

    private static void addIfNew(List<DocumentChunk> selected, Set<UUID> seen, DocumentChunk chunk) {
        if (chunk == null || chunk.getId() == null || seen.contains(chunk.getId())) {
            return;
        }
        selected.add(chunk);
        seen.add(chunk.getId());
    }

    static List<DocumentChunk> selectTopNByScore(List<ScoredChunk> scored,
                                                   int finalContextTopN,
                                                   int maxContextChars) {
        return selectTopNByScoreWithBudget(scored, finalContextTopN, maxContextChars, null).chunks();
    }

    static SelectionWithBudget selectTopNByScoreWithBudget(List<ScoredChunk> scored,
                                                           int finalContextTopN,
                                                           int maxContextChars) {
        return selectTopNByScoreWithBudget(scored, finalContextTopN, maxContextChars, null);
    }

    static SelectionWithBudget selectTopNByScoreWithBudget(List<ScoredChunk> scored,
                                                           int finalContextTopN,
                                                           int maxContextChars,
                                                           String question) {
        if (scored == null || scored.isEmpty()) {
            return new SelectionWithBudget(List.of(), false);
        }
        int limit = Math.max(1, finalContextTopN);
        List<DocumentChunk> selected = new ArrayList<>();
        int totalChars = 0;
        boolean budgetLimited = false;
        boolean hasNormalizedRow = false;
        QuerySignalExtractor.QuerySignals signals = QuerySignalExtractor.extract(question);
        boolean tableLikeQuery = KeywordSearchService.isTableLikeQuery(signals, question);
        for (ScoredChunk sc : scored) {
            if (selected.size() >= limit) {
                break;
            }
            DocumentChunk c = sc.chunk();
            String chunkType = c.getChunkType() == null ? "text" : c.getChunkType();
            if ("normalized_table_row".equals(chunkType)) {
                hasNormalizedRow = true;
            }
            if (tableLikeQuery && hasNormalizedRow && "text".equals(chunkType)
                    && NormalizedTableService.hasHighTableLikeDensity(
                    Optional.ofNullable(c.getContent()).orElse(""))) {
                continue;
            }
            String content = c.getContent() == null ? "" : c.getContent();
            if (totalChars + content.length() > maxContextChars && !selected.isEmpty()) {
                budgetLimited = true;
                break;
            }
            if (totalChars + content.length() > maxContextChars && selected.isEmpty()) {
                selected.add(c);
                totalChars += content.length();
                budgetLimited = true;
                break;
            }
            totalChars += content.length();
            selected.add(c);
        }
        if (tableLikeQuery && CellAwareTableRowScorer.isCompareQuery(question, signals)) {
            long diversityStart = RagLatencyTrace.now();
            selected = applyCompareLabelCoverage(selected, scored, signals, limit);
            RagLatencyTrace trace = RagLatencyTrace.current();
            if (trace != null) {
                trace.addSourceDiversityMs(RagLatencyTrace.elapsedMs(diversityStart));
            }
        } else if (tableLikeQuery && signals != null && !signals.identifiers().isEmpty()) {
            long diversityStart = RagLatencyTrace.now();
            selected = applyExactIdentifierSourceDiversity(selected, scored, signals, limit);
            RagLatencyTrace trace = RagLatencyTrace.current();
            if (trace != null) {
                trace.addSourceDiversityMs(RagLatencyTrace.elapsedMs(diversityStart));
            }
        }
        return new SelectionWithBudget(selected, budgetLimited);
    }

    static List<DocumentChunk> applyExactIdentifierSourceDiversity(
            List<DocumentChunk> selected,
            List<ScoredChunk> scored,
            QuerySignalExtractor.QuerySignals signals,
            int limit
    ) {
        if (selected == null || scored == null || signals == null || signals.identifiers().isEmpty()) {
            return selected == null ? List.of() : selected;
        }
        List<DocumentChunk> result = new ArrayList<>(selected);
        Set<String> selectedSources = result.stream()
                .filter(c -> "normalized_table_row".equals(c.getChunkType()))
                .filter(c -> CellAwareTableRowScorer.hasExactCellMatch(c, signals))
                .map(RagRetrievalService::tableSourceKey)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (selectedSources.size() != 1) {
            return result;
        }
        Optional<ScoredChunk> alternative = scored.stream()
                .filter(sc -> "normalized_table_row".equals(sc.chunk().getChunkType()))
                .filter(sc -> CellAwareTableRowScorer.hasExactCellMatch(sc.chunk(), signals))
                .filter(sc -> !selectedSources.contains(tableSourceKey(sc.chunk())))
                .filter(sc -> result.stream().noneMatch(c -> c.getId() != null
                        && c.getId().equals(sc.chunk().getId())))
                .min(Comparator
                        .comparingInt((ScoredChunk sc) -> identifierNoise(sc.chunk(), signals))
                        .thenComparing(Comparator.comparingDouble(ScoredChunk::finalScore).reversed()));
        if (alternative.isEmpty()) {
            return result;
        }
        int noisySelectedIndex = -1;
        int noisySelectedScore = 0;
        int alternativeNoise = identifierNoise(alternative.get().chunk(), signals);
        for (int i = 0; i < result.size(); i++) {
            DocumentChunk c = result.get(i);
            if (!"normalized_table_row".equals(c.getChunkType())
                    || !CellAwareTableRowScorer.hasExactCellMatch(c, signals)) {
                continue;
            }
            int noise = identifierNoise(c, signals);
            if (noise > noisySelectedScore) {
                noisySelectedScore = noise;
                noisySelectedIndex = i;
            }
        }
        if (noisySelectedIndex >= 0 && noisySelectedScore > alternativeNoise + 1) {
            result.set(noisySelectedIndex, alternative.get().chunk());
        } else {
            if (result.size() >= Math.max(1, limit) && !result.isEmpty()) {
                result.remove(result.size() - 1);
            }
            result.add(alternative.get().chunk());
        }
        return result;
    }

    private static int identifierNoise(DocumentChunk chunk, QuerySignalExtractor.QuerySignals signals) {
        if (chunk == null || signals == null) {
            return 0;
        }
        Set<String> queryIds = signals.identifiers().stream()
                .map(QuerySignalExtractor::normalize)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> rowIds = QuerySignalExtractor.extract(
                        Optional.ofNullable(chunk.getCellsJson()).orElse("") + " "
                                + Optional.ofNullable(chunk.getContent()).orElse(""))
                .identifiers().stream()
                .map(QuerySignalExtractor::normalize)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        rowIds.removeAll(queryIds);
        return rowIds.size();
    }

    private static String tableSourceKey(DocumentChunk chunk) {
        if (chunk == null) {
            return "";
        }
        String section = Optional.ofNullable(chunk.getSectionId()).orElse("");
        String table = Optional.ofNullable(chunk.getTableName()).orElse("");
        return section + "|" + table;
    }

    static List<DocumentChunk> applyCompareLabelCoverage(
            List<DocumentChunk> selected,
            List<ScoredChunk> scored,
            QuerySignalExtractor.QuerySignals signals,
            int limit
    ) {
        if (selected == null || scored == null || signals == null) {
            return selected == null ? List.of() : selected;
        }
        List<CellAwareTableRowScorer.ParsedStructuredLabel> labels =
                CellAwareTableRowScorer.parseLabels(signals.structuredLabels());
        if (labels.size() < 2) {
            return selected;
        }
        List<DocumentChunk> result = new ArrayList<>(selected);
        Set<String> coveredLabels = new LinkedHashSet<>();
        for (DocumentChunk c : result) {
            if (!"normalized_table_row".equals(c.getChunkType())) {
                continue;
            }
            Map<String, String> cells = CellAwareTableRowScorer.parseCells(c);
            for (CellAwareTableRowScorer.ParsedStructuredLabel label : labels) {
                for (Map.Entry<String, String> cell : cells.entrySet()) {
                    if (CellAwareTableRowScorer.cellMatchesLabel(
                            cell.getKey(), cell.getValue(), label)
                            || CellAwareTableRowScorer.valueCoverageMatch(cell.getValue(), label)) {
                        coveredLabels.add(label.raw());
                    }
                }
            }
        }
        List<String> missing = new ArrayList<>();
        for (CellAwareTableRowScorer.ParsedStructuredLabel label : labels) {
            if (!coveredLabels.contains(label.raw())) {
                missing.add(label.raw());
            }
        }
        if (missing.isEmpty()) {
            return result;
        }
        log.info("[RAG][compare-coverage] missingEntityCoverage labels={}", missing);
        for (String missingLabel : missing) {
            CellAwareTableRowScorer.ParsedStructuredLabel target = labels.stream()
                    .filter(l -> l.raw().equals(missingLabel))
                    .findFirst()
                    .orElse(null);
            if (target == null) {
                continue;
            }
            Optional<ScoredChunk> best = scored.stream()
                    .filter(sc -> "normalized_table_row".equals(sc.chunk().getChunkType()))
                    .filter(sc -> {
                        Map<String, String> cells = CellAwareTableRowScorer.parseCells(sc.chunk());
                        return cells.entrySet().stream().anyMatch(e ->
                                CellAwareTableRowScorer.cellMatchesLabel(e.getKey(), e.getValue(), target)
                                || CellAwareTableRowScorer.valueCoverageMatch(e.getValue(), target));
                    })
                    .max(Comparator.comparingDouble(ScoredChunk::finalScore));
            if (best.isPresent() && result.stream().noneMatch(c -> c.getId() != null
                    && c.getId().equals(best.get().chunk().getId()))) {
                if (result.size() >= limit && !result.isEmpty()) {
                    result.remove(result.size() - 1);
                }
                result.add(best.get().chunk());
            }
        }
        List<DocumentChunk> filtered = new ArrayList<>();
        for (DocumentChunk c : result) {
            if (rowConflictsWithCompareLabels(c, labels)) {
                continue;
            }
            filtered.add(c);
        }
        return filtered;
    }

    static boolean rowConflictsWithCompareLabels(
            DocumentChunk chunk,
            List<CellAwareTableRowScorer.ParsedStructuredLabel> labels
    ) {
        if (chunk == null || !"normalized_table_row".equals(chunk.getChunkType()) || labels == null) {
            return false;
        }
        Map<String, String> cells = CellAwareTableRowScorer.parseCells(chunk);
        boolean matchesAnyLabel = labels.stream().anyMatch(label -> cells.entrySet().stream()
                .anyMatch(e -> CellAwareTableRowScorer.cellMatchesLabel(
                        e.getKey(), e.getValue(), label)
                        || CellAwareTableRowScorer.valueCoverageMatch(e.getValue(), label)));
        if (matchesAnyLabel) {
            return false;
        }
        return labels.stream().anyMatch(label -> cells.entrySet().stream()
                .anyMatch(e -> CellAwareTableRowScorer.cellConflictsWithLabel(
                        e.getKey(), e.getValue(), label)));
    }

    static List<ScoredChunk> demoteLeakyTextCandidates(String question, List<ScoredChunk> scored) {
        if (scored == null || scored.isEmpty()) {
            return List.of();
        }
        QuerySignalExtractor.QuerySignals signals = QuerySignalExtractor.extract(question);
        if (!KeywordSearchService.isTableLikeQuery(signals, question)) {
            return scored;
        }
        boolean hasNormalized = scored.stream()
                .anyMatch(sc -> "normalized_table_row".equals(sc.chunk().getChunkType()));
        if (!hasNormalized) {
            return scored;
        }
        boolean hasExactRow = scored.stream()
                .anyMatch(sc -> CellAwareTableRowScorer.hasExactCellMatch(sc.chunk(), signals));
        List<ScoredChunk> adjusted = new ArrayList<>();
        for (ScoredChunk sc : scored) {
            DocumentChunk c = sc.chunk();
            double score = sc.finalScore();
            if ("text".equals(c.getChunkType())
                    && NormalizedTableService.hasHighTableLikeDensity(
                    Optional.ofNullable(c.getContent()).orElse(""))) {
                score *= 0.2;
            } else if (CellAwareTableRowScorer.isTableLikeSummary(c) && hasExactRow) {
                score *= 0.35;
            } else if ("normalized_table_row".equals(c.getChunkType())) {
                score *= 1.12;
            }
            adjusted.add(new ScoredChunk(c, score));
        }
        return adjusted.stream()
                .sorted(Comparator.comparingDouble(ScoredChunk::finalScore).reversed())
                .toList();
    }

    static List<ScoredChunk> applyCellAwareScoreBoost(String question, List<ScoredChunk> scored) {
        if (scored == null || scored.isEmpty() || question == null) {
            return scored == null ? List.of() : scored;
        }
        QuerySignalExtractor.QuerySignals signals = QuerySignalExtractor.extract(question);
        if (!KeywordSearchService.isTableLikeQuery(signals, question)) {
            return scored;
        }
        double maxCell = 0.0;
        Map<UUID, CellAwareTableRowScorer.CellAwareScore> cellScores = new HashMap<>();
        for (ScoredChunk sc : scored) {
            if (!"normalized_table_row".equals(sc.chunk().getChunkType())) {
                continue;
            }
            CellAwareTableRowScorer.CellAwareScore cs =
                    CellAwareTableRowScorer.score(sc.chunk(), signals, question);
            if (sc.chunk().getId() != null) {
                cellScores.put(sc.chunk().getId(), cs);
            }
            maxCell = Math.max(maxCell, cs.total());
        }
        if (maxCell <= 0) {
            return scored;
        }
        double scale = 0.25 / maxCell;
        List<ScoredChunk> adjusted = new ArrayList<>();
        for (ScoredChunk sc : scored) {
            double score = sc.finalScore();
            if (sc.chunk().getId() != null && cellScores.containsKey(sc.chunk().getId())) {
                score += cellScores.get(sc.chunk().getId()).total() * scale;
            }
            adjusted.add(new ScoredChunk(sc.chunk(), score));
        }
        return adjusted.stream()
                .sorted(Comparator.comparingDouble(ScoredChunk::finalScore).reversed())
                .toList();
    }

    static List<ScoredChunk> applyTableRowPriorityAdjustments(String question, List<ScoredChunk> scored) {
        if (scored == null || scored.isEmpty()) {
            return List.of();
        }
        QuerySignalExtractor.QuerySignals signals = QuerySignalExtractor.extract(question);
        if (!KeywordSearchService.isTableLikeQuery(signals, question)) {
            return scored;
        }
        boolean hasExactRow = scored.stream()
                .anyMatch(sc -> CellAwareTableRowScorer.hasExactCellMatch(sc.chunk(), signals));
        List<ScoredChunk> adjusted = new ArrayList<>();
        for (ScoredChunk sc : scored) {
            DocumentChunk c = sc.chunk();
            double score = sc.finalScore();
            if ("normalized_table_row".equals(c.getChunkType())) {
                score += 0.15;
                CellAwareTableRowScorer.CellAwareScore cs =
                        CellAwareTableRowScorer.score(c, signals, question);
                if (cs.total() > 0) {
                    score += Math.min(0.35, cs.total() * 0.02);
                }
            } else if (CellAwareTableRowScorer.isTableLikeSummary(c) && hasExactRow) {
                score -= 0.35;
            } else if ("table_summary".equals(c.getChunkType()) && hasExactRow
                    && !signals.identifiers().isEmpty()) {
                score -= 0.15;
            } else if ("text".equals(c.getChunkType())
                    && NormalizedTableService.hasHighTableLikeDensity(
                    Optional.ofNullable(c.getContent()).orElse(""))) {
                score -= 0.25;
            }
            adjusted.add(new ScoredChunk(c, Math.max(0.0, score)));
        }
        return adjusted.stream()
                .sorted(Comparator.comparingDouble(ScoredChunk::finalScore).reversed())
                .toList();
    }

    private void logCellAwareNormalizedRows(String question, List<ScoredChunk> scored) {
        if (question == null || scored == null || scored.isEmpty()) {
            return;
        }
        QuerySignalExtractor.QuerySignals signals = QuerySignalExtractor.extract(question);
        if (!KeywordSearchService.isTableLikeQuery(signals, question)) {
            return;
        }
        log.info("[RAG][cell-aware] signals identifiers={} labels={} ngrams={}",
                signals.identifiers(), signals.structuredLabels(),
                signals.ngrams().size() > 5 ? signals.ngrams().subList(0, 5) : signals.ngrams());
        int logged = 0;
        for (ScoredChunk sc : scored) {
            if (!"normalized_table_row".equals(sc.chunk().getChunkType())) {
                continue;
            }
            if (logged >= 10) {
                break;
            }
            DocumentChunk c = sc.chunk();
            CellAwareTableRowScorer.CellAwareScore cs =
                    CellAwareTableRowScorer.score(c, signals, question);
            String cellsExcerpt = Optional.ofNullable(c.getCellsJson()).orElse("");
            if (cellsExcerpt.length() > 120) {
                cellsExcerpt = cellsExcerpt.substring(0, 120) + "…";
            }
            if (cellsExcerpt.isBlank()) {
                cellsExcerpt = CellAwareTableRowScorer.parseCells(c).toString();
                if (cellsExcerpt.length() > 120) {
                    cellsExcerpt = cellsExcerpt.substring(0, 120) + "…";
                }
            }
            log.info("[RAG][cell-aware-top] rank={} rowIndex={} table={} cellScore={} finalScore={} cells={}",
                    logged + 1,
                    c.getRowIndex(),
                    c.getTableName(),
                    String.format("%.2f", cs.total()),
                    String.format("%.4f", sc.finalScore()),
                    cellsExcerpt);
            logged++;
        }
    }

    static List<DocumentChunk> sortByDocumentOrder(List<DocumentChunk> chunks, boolean prioritizeSummaries) {
        if (chunks == null || chunks.isEmpty()) {
            return List.of();
        }
        List<DocumentChunk> sorted = chunks.stream()
                .sorted(Comparator
                        .comparing((DocumentChunk c) -> c.getDocument() != null
                                ? c.getDocument().getId().toString() : "")
                        .thenComparingInt(c -> Optional.ofNullable(c.getSectionOrder()).orElse(0))
                        .thenComparingInt(c -> Optional.ofNullable(c.getOrderIndex()).orElse(0)))
                .toList();
        if (prioritizeSummaries) {
            return prioritizeSummaryChunks(sorted);
        }
        return sorted;
    }

    private int resolveMaxContextChars(QueryAnalyzerService.QueryType queryType, boolean isLockedScope) {
        if (isLockedScope) {
            return MAX_CONTEXT_CHARS_LOCKED;
        }
        if (isExpanded(queryType)) {
            return MAX_CONTEXT_CHARS_EXPANDED;
        }
        return MAX_CONTEXT_CHARS;
    }

    private RetrievedContext toRetrievedContext(DocumentChunk c) {
        String content = c.getContent() == null ? "" : c.getContent();
        return RetrievedContext.builder()
                .chunkId(c.getId())
                .documentId(c.getDocument().getId())
                .fileName(c.getSourceFile())
                .content(content)
                .cellsJson(c.getCellsJson())
                .tableName(c.getTableName())
                .rowIndex(c.getRowIndex())
                .groupContext(c.getGroupContext())
                .chunkType(c.getChunkType())
                .sectionId(c.getSectionId())
                .sectionTitle(c.getSectionTitle())
                .headingPathText(c.getHeadingPathText())
                .pageStart(c.getPageStart())
                .pageEnd(c.getPageEnd())
                .build();
    }

    private List<String> extractQueryTermsForScoring(String question) {
        if (question == null || question.isBlank()) {
            return List.of();
        }
        return Arrays.stream(normalizeForSearch(question).split("[^\\p{L}\\p{N}]+"))
                .map(String::trim)
                .filter(term -> term.length() >= 2)
                .distinct()
                .toList();
    }

    private Map<String, Double> computeIdfWeights(List<DocumentChunk> candidates, List<String> terms) {
        if (terms.isEmpty() || candidates.isEmpty()) {
            return Map.of();
        }
        int n = candidates.size();
        Map<String, Double> idf = new LinkedHashMap<>();
        for (String term : terms) {
            String t = normalizeForSearch(term);
            if (t.isBlank()) {
                continue;
            }
            long df = candidates.stream()
                    .filter(c -> chunkHaystack(c).contains(t))
                    .count();
            idf.put(t, Math.log((n + 1.0) / (df + 1.0)));
        }
        return idf;
    }

    private double lexicalIdfScore(DocumentChunk chunk, Map<String, Double> idfByTerm) {
        if (idfByTerm.isEmpty()) {
            return 0.0;
        }
        String haystack = chunkHaystack(chunk);
        double score = 0.0;
        for (Map.Entry<String, Double> entry : idfByTerm.entrySet()) {
            if (haystack.contains(entry.getKey())) {
                score += entry.getValue();
            }
        }
        return score;
    }

    private String chunkHaystack(DocumentChunk chunk) {
        String content = normalizeForSearch(Optional.ofNullable(chunk.getContent()).orElse(""));
        String heading = normalizeForSearch(Optional.ofNullable(chunk.getHeadingPathText()).orElse(""));
        String sectionTitle = normalizeForSearch(Optional.ofNullable(chunk.getSectionTitle()).orElse(""));
        return (heading + " " + sectionTitle + " " + content).trim();
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
    private static List<DocumentChunk> prioritizeSummaryChunks(List<DocumentChunk> sorted) {
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
    // TOP-N (final contexts) & fixed vector anchor K
    // ================================================================

    int fixedVectorAnchorK() {
        return Math.max(1, Math.min(30, configuredVectorAnchorK));
    }

    public static int normalizeFinalContextTopN(Integer requestedTopN) {
        if (requestedTopN == null) {
            return DEFAULT_FINAL_CONTEXT_TOP_N;
        }
        return Math.max(MIN_FINAL_CONTEXT_TOP_N, Math.min(MAX_FINAL_CONTEXT_TOP_N, requestedTopN));
    }

    public static int resolveFinalContextTopN(Integer override, QueryAnalyzerService.QueryType queryType) {
        if (override != null) {
            return normalizeFinalContextTopN(override);
        }
        return defaultFinalContextTopNForQueryType(queryType);
    }

    static int defaultFinalContextTopNForQueryType(QueryAnalyzerService.QueryType queryType) {
        if (queryType == null) {
            return DEFAULT_FINAL_CONTEXT_TOP_N;
        }
        return switch (queryType) {
            case LIST_ALL, COUNT_QUERY, SECTION_SUMMARY -> 20;
            case TABLE_LOOKUP -> 10;
            default -> DEFAULT_FINAL_CONTEXT_TOP_N;
        };
    }

    /**
     * @deprecated UI topK is final context top-N; vector anchor K is fixed via config.
     */
    @Deprecated
    public static int normalizeAnchorTopK(Integer ignored) {
        return DEFAULT_VECTOR_ANCHOR_K;
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
