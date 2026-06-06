package KLTN.RAG_CHATBOT_BE.rag.analysis;

import KLTN.RAG_CHATBOT_BE.domain.document.DocumentSection;
import KLTN.RAG_CHATBOT_BE.domain.document.DocumentSectionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Orchestrator for query intent classification.
 *
 * <p>Classification pipeline (in order):
 * <ol>
 *   <li>{@link LocalGenericQueryAnalyzer} — structural signals only, no domain words.</li>
 *   <li>Data-driven heading match — checks query terms against real DB section titles.</li>
 *   <li>{@link LlmQueryClassifier} — LLM call when local confidence is below threshold.</li>
 *   <li>Safe fallback — returns local result or NORMAL_FACT when LLM is disabled / fails.</li>
 * </ol>
 *
 * <p>The old {@link #analyze(String, UUID)} and {@link #analyze(String)} methods remain
 * compatible for all existing callers. New callers should prefer {@link #analyzeDetailed}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QueryAnalyzerService {

    public enum QueryType {
        /** Simple factual question. */
        NORMAL_FACT,
        /** Needs full enumeration of all items. */
        LIST_ALL,
        /** Needs a table cell / row attribute lookup. */
        TABLE_LOOKUP,
        /** Asks about the content / summary of a section. */
        SECTION_SUMMARY,
        /** Asks for a count: how many. */
        COUNT_QUERY,
        /** Section spans multiple pages. */
        CROSS_PAGE_SECTION
    }

    /**
     * Match between a query and a document section heading.
     *
     * @param sectionKey  Section identifier, e.g. {@code "sec_6.2"}
     * @param title       Original title (not normalized)
     * @param headingPath Full heading path, e.g. {@code "6 Parent > 6.2 Child"}
     * @param titleHits   Number of query terms that matched the section title directly
     * @param totalScore  Composite score (see {@link #findMatchedSections(String, List)})
     */
    public record HeadingMatch(
            String sectionKey,
            String title,
            String headingPath,
            int titleHits,
            int totalScore
    ) {}

    private final DocumentSectionRepository documentSectionRepository;
    private final LocalGenericQueryAnalyzer localAnalyzer;
    private final LlmQueryClassifier llmClassifier;

    /** Invoke LLM classifier at all when local confidence is below this threshold. */
    @Value("${rag.analysis.llm-classifier.only-when-local-confidence-below:0.70}")
    private double triggerThreshold;

    /** Master switch — false keeps the classifier completely off (shadow or active). */
    @Value("${rag.analysis.llm-classifier.enabled:false}")
    private boolean llmEnabled;

    /**
     * Shadow mode: LLM runs async (fire-and-forget) for comparison logging.
     * Runtime result is always the local / heading-match result.
     * Set to false to use LLM result in the actual retrieval pipeline.
     */
    @Value("${rag.analysis.llm-classifier.shadow-mode:true}")
    private boolean shadowMode;

    // ===================================================================
    // PUBLIC API
    // ===================================================================

    /**
     * Full classification with explainability fields.
     *
     * @param question the raw user question
     * @param widgetId optional; enables data-driven heading match when provided
     */
    public QueryAnalysisResult analyzeDetailed(String question, UUID widgetId) {
        if (question == null || question.isBlank()) {
            return QueryAnalysisResult.ofLocal(QueryType.NORMAL_FACT, 1.0, List.of("empty query"));
        }

        // Step 1: generic structural signals (no DB, no LLM, no domain lists)
        QueryAnalysisResult localResult = localAnalyzer.analyze(question);

        // Step 2: if structural confidence is high enough, return immediately
        if (localResult.confidence() >= triggerThreshold) {
            return localResult;
        }

        // Step 3: data-driven heading match from actual DB section titles
        if (widgetId != null) {
            String q = normalize(question);
            if (isLikelyHeadingQuery(q, widgetId)) {
                QueryAnalysisResult headingResult = QueryAnalysisResult.ofHeadingMatch(
                        QueryType.SECTION_SUMMARY, 0.85,
                        List.of("query terms overlap with DB section headings"));
                return headingResult;
            }
        }

        // Step 4: LLM classifier (when enabled)
        if (llmEnabled) {
            if (shadowMode) {
                CompletableFuture.runAsync(() -> {
                    try {
                        llmClassifier.classify(question);
                    } catch (Exception ex) {
                        log.warn("[QueryAnalysis] shadow=true llmCallFailed={}", ex.getMessage());
                    }
                });
                // Always return local result in shadow mode
                return localResult;
            } else {
                // Active mode: use LLM when valid; fallback to local on failure/low confidence
                try {
                    QueryAnalysisResult llmResult = llmClassifier.classify(question);
                    boolean llmFallback = llmResult.source() == QueryAnalysisSource.FALLBACK_DEFAULT;
                    return llmFallback ? localResult : llmResult;
                } catch (Exception ex) {
                    log.warn("[QueryAnalysis] active=true llmCallFailed={} — using local result",
                            ex.getMessage());
                }
            }
        }

        // Step 5: fallback — return local result (best-effort, low confidence is OK)
        return localResult;
    }

    /** Compatibility wrapper — returns {@link QueryType} from {@link #analyzeDetailed}. */
    public QueryType analyze(String question, UUID widgetId) {
        return analyzeDetailed(question, widgetId).queryType();
    }

    /** Compatibility wrapper — no widget context. */
    public QueryType analyze(String question) {
        return analyzeDetailed(question, null).queryType();
    }

    // ===================================================================
    // HEADING MATCH  —  fetches sections from DB (data-driven, no hardcoded terms)
    // ===================================================================

    /**
     * Find sections whose title/heading path best matches the query.
     * Fetches sections from DB automatically.
     *
     * @return matches sorted descending by totalScore (best match at index 0)
     */
    public List<HeadingMatch> findMatchedSections(String question, UUID widgetId) {
        if (question == null || question.isBlank() || widgetId == null) return List.of();
        List<DocumentSection> sections =
                documentSectionRepository.findByWidgetConfigIdOrderByOrderIndexAsc(widgetId);
        return findMatchedSections(question, sections);
    }

    /**
     * Find sections whose title/heading path best matches the query.
     * Uses pre-fetched section list (avoids extra DB round-trip).
     *
     * <p>Scoring formula:
     * <pre>
     * base      = titleHits × 3  +  pathOnlyHits × 1
     * penalty   = missedTerms    × 2
     * bonus1    = significantTerms.size() × 2   (if title contains the full query phrase)
     * bonus2    = significantTerms.size()        (if ALL terms hit directly in title)
     * totalScore = base − penalty + bonus1 + bonus2
     * </pre>
     *
     * <p>Qualification: (titleHits ≥ 2 OR (titleHits ≥ 1 AND pathOnlyHits ≥ 1)) AND score > 0.
     *
     * @return up to 5 matches, index-0 is the best match
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
            return List.of();
        }

        int totalTerms = significantTerms.size();

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
            int score = titleHits * 3 + pathOnlyHits;
            score -= missedTerms * 2;

            if (!title.isBlank() && !q.isBlank() && title.contains(q.trim())) {
                score += totalTerms * 2;
            }

            if (titleHits == totalTerms && totalTerms >= 2) {
                score += totalTerms;
            }

            boolean qualifies = (titleHits >= 2
                    || (titleHits >= 1 && pathOnlyHits >= 1 && totalTerms >= 2))
                    && score > 0;

            if (qualifies) {
                matches.add(new HeadingMatch(
                        sec.getSectionKey(), sec.getTitle(), sec.getHeadingPathText(),
                        titleHits, score));
            }
        }

        List<HeadingMatch> top = matches.stream()
                .sorted((a, b) -> {
                    int cmp = Integer.compare(b.totalScore(), a.totalScore());
                    if (cmp != 0) return cmp;
                    cmp = Integer.compare(b.titleHits(), a.titleHits());
                    if (cmp != 0) return cmp;
                    int aDepth = a.sectionKey().split("\\.").length;
                    int bDepth = b.sectionKey().split("\\.").length;
                    return Integer.compare(bDepth, aDepth);
                })
                .limit(5)
                .toList();

        return top;
    }

    // ===================================================================
    // QUERY REWRITING  —  conservative generic variants, no domain lists
    // ===================================================================

    /**
     * Generate query variants for vector search (deduplication is handled downstream).
     *
     * <p>Conservative strategy — three generic forms:
     * <ol>
     *   <li>Original trimmed</li>
     *   <li>Punctuation-stripped (trailing {@code ? ？ .} removed)</li>
     *   <li>Normalized (NFD, lowercase, diacritics removed)</li>
     * </ol>
     */
    public List<String> rewriteQuery(String question) {
        List<String> variants = new ArrayList<>();
        if (question == null || question.isBlank()) return variants;

        String trimmed = question.trim();
        variants.add(trimmed);

        // Punctuation-stripped form
        String punctStripped = trimmed.replaceAll("[?？.]+$", "").trim();
        if (!punctStripped.equalsIgnoreCase(trimmed) && !punctStripped.isBlank()) {
            variants.add(punctStripped);
        }

        // Normalized form (diacritics removed, lowercase)
        String norm = normalize(trimmed);
        if (!norm.equalsIgnoreCase(trimmed) && !norm.isBlank()) {
            variants.add(norm);
        }

        return variants.stream().distinct().toList();
    }

    // ===================================================================
    // NORMALIZATION UTILITIES
    // ===================================================================

    /** NFD normalize, strip diacritics, replace đ→d, lowercase, collapse whitespace. */
    public String normalize(String value) {
        if (value == null) return "";
        String n = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace('đ', 'd')
                .replace('Đ', 'D')
                .toLowerCase(Locale.ROOT);
        return n.replaceAll("\\s+", " ").trim();
    }

    // ===================================================================
    // PRIVATE HELPERS
    // ===================================================================

    /**
     * Data-driven heading check: returns true if at least 2 significant query terms
     * (length ≥ 4) appear in any section title or heading path for this widget.
     * Purely driven by actual DB content — no hardcoded keyword lists.
     */
    private boolean isLikelyHeadingQuery(String normalizedQuestion, UUID widgetId) {
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

    private String safeStr(String v) {
        return v == null ? "" : v;
    }
}
