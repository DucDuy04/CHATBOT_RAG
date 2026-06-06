package KLTN.RAG_CHATBOT_BE.rag.rerank;

import KLTN.RAG_CHATBOT_BE.rag.analysis.QueryAnalyzerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Config-driven guard that decides whether to skip external Cohere rerank
 * for low-value cases, saving ~300–700ms per skipped call.
 *
 * <p>Guard rules (evaluated in order, first match wins):
 * <ol>
 *   <li><b>A — CANDIDATE_COUNT_LTE_THRESHOLD</b>: skip when candidate count ≤ threshold.
 *       Rerank adds little value over very small sets.</li>
 *   <li><b>B — TOP_SCORE_GAP</b>: skip when the cheap pre-score gap between rank-1
 *       and rank-2 is already large enough that rerank is unlikely to change selection.</li>
 *   <li><b>D — EXACT_LOOKUP_STRONG_CELL_MATCH</b>: skip when the query is TABLE_LOOKUP
 *       and the top cheap pre-score meets the confidence threshold — the exact row is
 *       already identified with high confidence.</li>
 * </ol>
 *
 * <p>Rule C (OOS skip) is deferred — no reliable OOS QueryType signal exists yet.
 *
 * <p>When the guard is disabled ({@code rerank-guard.enabled=false}), behavior is
 * identical to the pre-guard state (rerank always called when globally enabled).
 */
@Slf4j
@Component
public class RerankGuard {

    public enum SkipReason {
        /** Guard is disabled or rerank is globally off — normal rerank path. */
        NOT_SKIPPED,
        /** Guard disabled via config. */
        DISABLED,
        /** Candidate count is at or below the threshold — rerank adds minimal value. */
        CANDIDATE_COUNT_LTE_THRESHOLD,
        /** Pre-score gap between rank-1 and rank-2 is already above threshold. */
        TOP_SCORE_GAP,
        /** TABLE_LOOKUP query with a top pre-score at or above cell-match confidence threshold. */
        EXACT_LOOKUP_STRONG_CELL_MATCH
    }

    /**
     * Decision returned by {@link #decide}.
     *
     * @param shouldSkip      true if rerank should be skipped
     * @param reason          the skip reason enum; {@link SkipReason#NOT_SKIPPED} when not skipped
     * @param candidateCount  number of candidates that would be sent to rerank
     * @param topScoreGap     rank-1 minus rank-2 cheap pre-score (0 when < 2 candidates)
     */
    public record Decision(
            boolean shouldSkip,
            SkipReason reason,
            int candidateCount,
            double topScoreGap
    ) {
        static Decision noSkip(int candidateCount) {
            return new Decision(false, SkipReason.NOT_SKIPPED, candidateCount, 0.0);
        }
    }

    @Value("${rag.retrieval.rerank-guard.enabled:true}")
    private boolean guardEnabled;

    @Value("${rag.retrieval.rerank-guard.skip-when-candidates-lte:5}")
    private int skipWhenCandidatesLte;

    @Value("${rag.retrieval.rerank-guard.skip-when-top-score-gap-gte:0.35}")
    private double skipWhenTopScoreGapGte;

    @Value("${rag.retrieval.rerank-guard.skip-exact-lookup-when-cell-score-gte:0.80}")
    private double skipExactLookupWhenCellScoreGte;

    /**
     * Decide whether to skip external rerank.
     *
     * @param queryType          detected query intent; may be null
     * @param candidateCount     number of candidates in the expensive scoring subset
     * @param topCheapScore      cheap pre-score of rank-1 candidate (0.0 if no candidates)
     * @param secondCheapScore   cheap pre-score of rank-2 candidate (0.0 if fewer than 2)
     * @return decision record; never null
     */
    public Decision decide(
            QueryAnalyzerService.QueryType queryType,
            int candidateCount,
            double topCheapScore,
            double secondCheapScore
    ) {
        if (!guardEnabled) {
            return Decision.noSkip(candidateCount);
        }

        // Rule A — too few candidates
        if (candidateCount <= skipWhenCandidatesLte) {
            return new Decision(true, SkipReason.CANDIDATE_COUNT_LTE_THRESHOLD, candidateCount, 0.0);
        }

        // Rule B — high confidence score gap
        double topScoreGap = topCheapScore - secondCheapScore;
        if (topScoreGap >= skipWhenTopScoreGapGte) {
            return new Decision(true, SkipReason.TOP_SCORE_GAP, candidateCount, topScoreGap);
        }

        // Rule D — exact TABLE_LOOKUP with strong pre-score match
        if (queryType == QueryAnalyzerService.QueryType.TABLE_LOOKUP
                && topCheapScore >= skipExactLookupWhenCellScoreGte) {
            return new Decision(true, SkipReason.EXACT_LOOKUP_STRONG_CELL_MATCH, candidateCount, topScoreGap);
        }

        return Decision.noSkip(candidateCount);
    }
}
