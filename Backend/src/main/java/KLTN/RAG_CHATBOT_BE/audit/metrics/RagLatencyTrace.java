package KLTN.RAG_CHATBOT_BE.audit.metrics;

import lombok.extern.slf4j.Slf4j;

import java.security.SecureRandom;
import java.util.Locale;

@Slf4j
public final class RagLatencyTrace implements AutoCloseable {

    private static final ThreadLocal<RagLatencyTrace> CURRENT = new ThreadLocal<>();
    private static final char[] TRACE_ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private final String traceId;
    private final long startNanos;

    private long queryAnalyzeMs;
    private long queryEmbedMs;
    private long vectorMs;
    private long dbMs;
    private long keywordMs;
    private long keywordIndexBuildMs;
    private long keywordPostingLookupMs;
    private long mergeMs;
    private long scoringMs;
    private long cellAwareMs;
    private long rerankMs;
    private long finalSortMs;
    private long sourceDiversityMs;
    private long contextSelectMs;
    private long promptBuildMs;
    private long llmFirstTokenMs;
    private long llmTotalMs;
    private long persistMs;
    private long sourceMs;
    private long sseTotalMs;

    private int queryAnalyzeCallCount;
    private String queryAnalysisType = "UNKNOWN";
    private String queryAnalysisSource = "UNKNOWN";
    private double queryAnalysisConfidence;
    private String queryAnalysisFallbackReason = "none";

    private int candidatesBefore;
    private int candidatesScored;
    private int vectorCandidates;
    private int keywordCandidatesFromIndex;
    private int mergedCandidatesBeforeDedupe;
    private int mergedCandidatesAfterDedupe;
    private int cheapPreScoreCandidates;
    private int expensiveCellAwareCandidates;
    private int finalRerankCandidates;
    private int keywordCandidatesDroppedByCheapGate;
    private int keywordCandidatesFromPostings;
    private int keywordCandidatesScored;
    private boolean keywordIndexHit;
    private boolean keywordFallbackScan;
    private int selectedContexts;
    private int contextChars;
    private int estimatedPromptTokens;
    private int outputTokens;
    private int requestedTopN;
    private int effectiveTopN;
    private int requestedMaxTokens;
    private int effectiveMaxTokens;

    private boolean rerankSkipped;
    private String rerankSkipReason = "NOT_SKIPPED";
    private int rerankGuardCandidateCount;

    // Async persist fields (task 27F)
    private String persistMode = "sync";

    // Query-variant dedupe fields (task 27D)
    private int queryVariantTotal;
    private int queryVariantUnique;
    private int queryVariantSkipped;
    private String queryVariantDedupeMode = "NONE";
    private int qdrantSearchCalls;

    private RagLatencyTrace(String traceId) {
        this.traceId = traceId;
        this.startNanos = System.nanoTime();
    }

    public static RagLatencyTrace begin() {
        RagLatencyTrace trace = new RagLatencyTrace(newTraceId());
        CURRENT.set(trace);
        return trace;
    }

    public static RagLatencyTrace current() {
        return CURRENT.get();
    }

    public static long now() {
        return System.nanoTime();
    }

    public static long elapsedMs(long startedNanos) {
        return Math.max(0, (System.nanoTime() - startedNanos) / 1_000_000L);
    }

    public String traceId() {
        return traceId;
    }

    public void addQueryAnalyzeMs(long ms) {
        queryAnalyzeMs += Math.max(0, ms);
    }

    public void recordQueryAnalysis(String queryType, String source, double confidence, String fallbackReason) {
        queryAnalyzeCallCount++;
        queryAnalysisType = queryType != null ? queryType : "UNKNOWN";
        queryAnalysisSource = source != null ? source : "UNKNOWN";
        queryAnalysisConfidence = Math.max(0.0, Math.min(1.0, confidence));
        queryAnalysisFallbackReason = fallbackReason != null && !fallbackReason.isBlank()
                ? fallbackReason
                : "none";
    }

    public void addQueryEmbedMs(long ms) {
        queryEmbedMs += Math.max(0, ms);
    }

    public void addVectorMs(long ms) {
        vectorMs += Math.max(0, ms);
    }

    public void addDbMs(long ms) {
        dbMs += Math.max(0, ms);
    }

    public void addKeywordMs(long ms) {
        keywordMs += Math.max(0, ms);
    }

    public void setKeywordIndexStats(boolean hit,
                                     long buildMs,
                                     long postingLookupMs,
                                     int candidatesFromPostings,
                                     int candidatesScored,
                                     boolean fallbackScan) {
        keywordIndexHit = hit;
        keywordIndexBuildMs = Math.max(0, buildMs);
        keywordPostingLookupMs = Math.max(0, postingLookupMs);
        keywordCandidatesFromPostings = Math.max(0, candidatesFromPostings);
        keywordCandidatesScored = Math.max(0, candidatesScored);
        keywordFallbackScan = fallbackScan;
    }

    public void addMergeMs(long ms) {
        mergeMs += Math.max(0, ms);
    }

    public void addScoringMs(long ms) {
        scoringMs += Math.max(0, ms);
    }

    public void addCellAwareMs(long ms) {
        cellAwareMs += Math.max(0, ms);
    }

    public void addRerankMs(long ms) {
        rerankMs += Math.max(0, ms);
    }

    public void addFinalSortMs(long ms) {
        finalSortMs += Math.max(0, ms);
    }

    public void addSourceDiversityMs(long ms) {
        sourceDiversityMs += Math.max(0, ms);
    }

    public void addContextSelectMs(long ms) {
        contextSelectMs += Math.max(0, ms);
    }

    public void addPromptBuildMs(long ms) {
        promptBuildMs += Math.max(0, ms);
    }

    public void addLlmFirstTokenMs(long ms) {
        if (llmFirstTokenMs <= 0) {
            llmFirstTokenMs = Math.max(0, ms);
        }
    }

    public void addLlmTotalMs(long ms) {
        llmTotalMs += Math.max(0, ms);
    }

    public void addPersistMs(long ms) {
        persistMs += Math.max(0, ms);
    }

    public void addSourceMs(long ms) {
        sourceMs += Math.max(0, ms);
    }

    public void setSseTotalMs(long ms) {
        sseTotalMs = Math.max(0, ms);
    }

    public void setCandidates(int before, int scored) {
        candidatesBefore = Math.max(0, before);
        candidatesScored = Math.max(0, scored);
    }

    public void setMergeCandidateStats(int vector,
                                       int keywordFromIndex,
                                       int beforeDedupe,
                                       int afterDedupe) {
        vectorCandidates = Math.max(0, vector);
        keywordCandidatesFromIndex = Math.max(0, keywordFromIndex);
        mergedCandidatesBeforeDedupe = Math.max(0, beforeDedupe);
        mergedCandidatesAfterDedupe = Math.max(0, afterDedupe);
    }

    public void setScoringCandidateStats(int cheapScored,
                                         int cellAwareScored,
                                         int finalRerankScored,
                                         int droppedByCheapGate) {
        cheapPreScoreCandidates = Math.max(0, cheapScored);
        expensiveCellAwareCandidates = Math.max(0, cellAwareScored);
        finalRerankCandidates = Math.max(0, finalRerankScored);
        keywordCandidatesDroppedByCheapGate = Math.max(0, droppedByCheapGate);
    }

    public void setContextStats(int selected, int chars) {
        selectedContexts = Math.max(0, selected);
        contextChars = Math.max(0, chars);
    }

    public void setEstimatedPromptTokens(int tokens) {
        estimatedPromptTokens = Math.max(0, tokens);
    }

    public void setOutputTokens(int tokens) {
        outputTokens = Math.max(0, tokens);
    }

    public void setTopN(int requested, int effective) {
        requestedTopN = Math.max(0, requested);
        effectiveTopN = Math.max(0, effective);
    }

    public void setMaxTokens(int requested, int effective) {
        requestedMaxTokens = Math.max(0, requested);
        effectiveMaxTokens = Math.max(0, effective);
    }

    public void setRerankGuardDecision(boolean skipped, String reason, int candidateCount) {
        rerankSkipped = skipped;
        rerankSkipReason = reason != null ? reason : "NOT_SKIPPED";
        rerankGuardCandidateCount = Math.max(0, candidateCount);
    }

    public void setPersistMode(String mode) {
        persistMode = mode != null ? mode : "sync";
    }

    public void setQueryVariantDedupeStats(int total, int unique, int skipped,
                                           String dedupeMode, int qdrantCalls) {
        queryVariantTotal  = Math.max(0, total);
        queryVariantUnique = Math.max(0, unique);
        queryVariantSkipped = Math.max(0, skipped);
        queryVariantDedupeMode = dedupeMode != null ? dedupeMode : "NONE";
        qdrantSearchCalls  = Math.max(0, qdrantCalls);
    }

    public void finish() {
        long retrievalMs = queryAnalyzeMs + queryEmbedMs + vectorMs + dbMs + keywordMs
                + keywordIndexBuildMs + mergeMs + scoringMs + sourceDiversityMs + contextSelectMs;
        log.info("[RAG][latency] trace={} totalMs={} retrievalMs={} queryAnalyzeMs={} queryAnalyzeCallCount={} "
                        + "queryAnalysisType={} queryAnalysisSource={} queryAnalysisConfidence={} "
                        + "queryAnalysisFallbackReason={} queryEmbedMs={} "
                        + "vectorMs={} dbMs={} keywordMs={} keywordIndexHit={} keywordIndexBuildMs={} "
                        + "keywordPostingLookupMs={} keywordCandidatesFromPostings={} "
                        + "keywordCandidatesScored={} keywordFallbackScan={} "
                        + "vectorCandidates={} keywordCandidatesFromIndex={} "
                        + "mergedCandidatesBeforeDedupe={} mergedCandidatesAfterDedupe={} "
                        + "cheapPreScoreCandidates={} expensiveCellAwareCandidates={} "
                        + "finalRerankCandidates={} keywordCandidatesDroppedByCheapGate={} "
                        + "mergeMs={} scoringMs={} cellAwareMs={} rerankMs={} finalSortMs={} sourceDiversityMs={} "
                        + "contextSelectMs={} promptContextBuildMs={} "
                        + "llmFirstTokenMs={} llmTotalMs={} persistMs={} sourceMs={} sseTotalMs={} "
                        + "selectedContexts={} contextChars={} estimatedPromptTokens={} "
                        + "candidatesBefore={} candidatesScored={} requestedTopN={} effectiveTopN={} "
                        + "requestedMaxTokens={} effectiveMaxTokens={} outputTokens={} "
                        + "rerankSkipped={} rerankSkipReason={} rerankGuardCandidates={} "
                        + "queryVariantTotal={} queryVariantUnique={} queryVariantSkipped={} "
                        + "queryVariantDedupeMode={} qdrantSearchCalls={} persistMode={}",
                traceId,
                elapsedMs(startNanos),
                retrievalMs,
                queryAnalyzeMs,
                queryAnalyzeCallCount,
                queryAnalysisType,
                queryAnalysisSource,
                String.format(Locale.ROOT, "%.2f", queryAnalysisConfidence),
                queryAnalysisFallbackReason,
                queryEmbedMs,
                vectorMs,
                dbMs,
                keywordMs,
                keywordIndexHit,
                keywordIndexBuildMs,
                keywordPostingLookupMs,
                keywordCandidatesFromPostings,
                keywordCandidatesScored,
                keywordFallbackScan,
                vectorCandidates,
                keywordCandidatesFromIndex,
                mergedCandidatesBeforeDedupe,
                mergedCandidatesAfterDedupe,
                cheapPreScoreCandidates,
                expensiveCellAwareCandidates,
                finalRerankCandidates,
                keywordCandidatesDroppedByCheapGate,
                mergeMs,
                scoringMs,
                cellAwareMs,
                rerankMs,
                finalSortMs,
                sourceDiversityMs,
                contextSelectMs,
                promptBuildMs,
                llmFirstTokenMs,
                llmTotalMs,
                persistMs,
                sourceMs,
                sseTotalMs,
                selectedContexts,
                contextChars,
                estimatedPromptTokens,
                candidatesBefore,
                candidatesScored,
                requestedTopN,
                effectiveTopN,
                requestedMaxTokens,
                effectiveMaxTokens,
                outputTokens,
                rerankSkipped,
                rerankSkipReason,
                rerankGuardCandidateCount,
                queryVariantTotal,
                queryVariantUnique,
                queryVariantSkipped,
                queryVariantDedupeMode,
                qdrantSearchCalls,
                persistMode);
    }

    @Override
    public void close() {
        finish();
        CURRENT.remove();
    }

    private static String newTraceId() {
        char[] out = new char[6];
        for (int i = 0; i < out.length; i++) {
            out[i] = TRACE_ALPHABET[RANDOM.nextInt(TRACE_ALPHABET.length)];
        }
        return new String(out).toLowerCase(Locale.ROOT);
    }
}
