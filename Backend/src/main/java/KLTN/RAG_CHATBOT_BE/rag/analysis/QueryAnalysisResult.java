package KLTN.RAG_CHATBOT_BE.rag.analysis;

import java.util.List;

/**
 * Detailed result of query intent classification.
 *
 * <p>Fields:
 * <ul>
 *   <li>{@code queryType}     — resolved intent type</li>
 *   <li>{@code confidence}    — [0.0, 1.0] classifier certainty</li>
 *   <li>{@code source}        — which classifier produced the result</li>
 *   <li>{@code evidence}      — human-readable reasons (never null)</li>
 *   <li>{@code llmUsed}       — true only when LLM classifier was called and result was accepted</li>
 *   <li>{@code fallbackReason}— non-null only when source is FALLBACK_DEFAULT</li>
 * </ul>
 */
public record QueryAnalysisResult(
        QueryAnalyzerService.QueryType queryType,
        double confidence,
        QueryAnalysisSource source,
        List<String> evidence,
        boolean llmUsed,
        String fallbackReason
) {

    /** Factory: result from generic local structural analysis. */
    public static QueryAnalysisResult ofLocal(
            QueryAnalyzerService.QueryType type, double confidence, List<String> evidence) {
        return new QueryAnalysisResult(
                type, clamp(confidence), QueryAnalysisSource.LOCAL_GENERIC,
                evidence != null ? List.copyOf(evidence) : List.of(),
                false, null);
    }

    /** Factory: result accepted from LLM classifier. */
    public static QueryAnalysisResult ofLlm(
            QueryAnalyzerService.QueryType type, double confidence, List<String> evidence) {
        return new QueryAnalysisResult(
                type, clamp(confidence), QueryAnalysisSource.LLM_CLASSIFIER,
                evidence != null ? List.copyOf(evidence) : List.of(),
                true, null);
    }

    /** Factory: result promoted by data-driven heading-term overlap. */
    public static QueryAnalysisResult ofHeadingMatch(
            QueryAnalyzerService.QueryType type, double confidence, List<String> evidence) {
        return new QueryAnalysisResult(
                type, clamp(confidence), QueryAnalysisSource.HEADING_MATCH,
                evidence != null ? List.copyOf(evidence) : List.of(),
                false, null);
    }

    /** Factory: safe default when no classifier produced a confident result. */
    public static QueryAnalysisResult ofFallback(QueryAnalyzerService.QueryType type, String reason) {
        return new QueryAnalysisResult(
                type, 0.5, QueryAnalysisSource.FALLBACK_DEFAULT,
                List.of(), false, reason);
    }

    private static double clamp(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }
}
