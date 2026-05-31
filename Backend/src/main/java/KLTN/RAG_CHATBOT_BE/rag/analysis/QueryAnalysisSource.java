package KLTN.RAG_CHATBOT_BE.rag.analysis;

/**
 * Indicates how a {@link QueryAnalysisResult} was produced.
 */
public enum QueryAnalysisSource {

    /** Classification from generic structural signals only — no domain word lists. */
    LOCAL_GENERIC,

    /** Classification from LLM classifier call. */
    LLM_CLASSIFIER,

    /** LLM classifier failed, timed out, or is disabled — default applied. */
    FALLBACK_DEFAULT,

    /** Compatibility shim returning legacy QueryType. */
    FALLBACK_LEGACY_COMPAT,

    /** Promoted to SECTION_SUMMARY because query terms overlap with actual DB section headings. */
    HEADING_MATCH
}
