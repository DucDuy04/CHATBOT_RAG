package KLTN.RAG_CHATBOT_BE.rag.retrieve;

import KLTN.RAG_CHATBOT_BE.domain.document.DocumentChunk;
import KLTN.RAG_CHATBOT_BE.ingest.normalize.NormalizedTableService;
import KLTN.RAG_CHATBOT_BE.rag.analysis.QuerySignalExtractor;
import KLTN.RAG_CHATBOT_BE.audit.metrics.RagLatencyTrace;
import KLTN.RAG_CHATBOT_BE.domain.document.DocumentChunkRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Generic in-memory BM25-like keyword search (task 23I).
 * Bounded scan per widget; no Elasticsearch; no domain-specific hardcoding.
 */
@Slf4j
@Service
public class KeywordSearchService {

    public enum CandidateSource {
        VECTOR, KEYWORD, BOTH
    }

    public record ScoredKeywordChunk(DocumentChunk chunk, double rawScore, double normalizedScore) {}

    public record KeywordSearchResult(
            List<ScoredKeywordChunk> topChunks,
            Map<UUID, Double> normalizedScoresByChunkId,
            QuerySignalExtractor.QuerySignals signals
    ) {
        public Set<UUID> chunkIds() {
            return topChunks.stream()
                    .map(sc -> sc.chunk().getId())
                    .filter(Objects::nonNull)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }
    }

    public record MergedCandidate(
            DocumentChunk chunk,
            double vectorScore,
            double keywordScore,
            CandidateSource source
    ) {}

    private final DocumentChunkRepository documentChunkRepository;
    private final KeywordIndexCache keywordIndexCache;

    @Value("${rag.retrieval.hybrid.enabled:true}")
    private boolean hybridEnabled;

    @Value("${rag.retrieval.hybrid.keyword-top-m:30}")
    private int keywordTopM;

    @Value("${rag.retrieval.hybrid.max-keyword-scan-chunks:3000}")
    private int maxKeywordScanChunks;

    @Value("${rag.retrieval.hybrid.exact-match-boost:2.0}")
    private double exactMatchBoost;

    @Value("${rag.retrieval.hybrid.phrase-match-boost:1.5}")
    private double phraseMatchBoost;

    @Value("${rag.retrieval.hybrid.table-row-boost:1.2}")
    private double tableRowBoost;

    @Value("${rag.retrieval.hybrid.table-summary-boost:0.8}")
    private double tableSummaryBoost;

    public KeywordSearchService(DocumentChunkRepository documentChunkRepository) {
        this(documentChunkRepository, new KeywordIndexCache(documentChunkRepository));
    }

    @Autowired
    public KeywordSearchService(DocumentChunkRepository documentChunkRepository,
                                KeywordIndexCache keywordIndexCache) {
        this.documentChunkRepository = documentChunkRepository;
        this.keywordIndexCache = keywordIndexCache;
    }

    public boolean isHybridEnabled() {
        return hybridEnabled;
    }

    public KeywordSearchResult search(String question, UUID widgetId) {
        if (!hybridEnabled || question == null || question.isBlank() || widgetId == null) {
            return emptyResult(question);
        }

        QuerySignalExtractor.QuerySignals signals = QuerySignalExtractor.extract(question);
        long indexLookupStart = RagLatencyTrace.now();
        KeywordIndexCache.LookupResult lookup = keywordIndexCache.lookup(widgetId, signals, maxKeywordScanChunks);
        List<DocumentChunk> corpus;
        List<DocumentChunk> candidates;
        Map<String, Double> idfByTerm;
        boolean fallbackScan = false;
        if (lookup.available()) {
            corpus = lookup.corpus();
            candidates = lookup.candidates();
            idfByTerm = lookup.idfByTerm();
        } else {
            fallbackScan = true;
            log.warn("[RAG][keyword-index] widget={} status=fallback reason={}", widgetId,
                    lookup.unavailableReason());
            corpus = loadBoundedCorpus(widgetId);
            candidates = corpus;
            idfByTerm = computeCorpusIdf(corpus, signals);
        }
        long indexLookupMs = lookup.available()
                ? lookup.postingLookupMs()
                : RagLatencyTrace.elapsedMs(indexLookupStart);
        RagLatencyTrace trace = RagLatencyTrace.current();
        if (trace != null) {
            trace.setKeywordIndexStats(
                    lookup.available(),
                    lookup.buildMs(),
                    indexLookupMs,
                    lookup.candidateCount(),
                    candidates.size(),
                    fallbackScan);
        }

        if (corpus.isEmpty()) {
            log.info("[RAG][hybrid] keywordCandidates=0 (empty corpus) widgetId={}", widgetId);
            return new KeywordSearchResult(List.of(), Map.of(), signals);
        }

        List<ScoredKeywordChunk> scored = new ArrayList<>();
        for (DocumentChunk chunk : candidates) {
            double raw = scoreChunk(chunk, signals, idfByTerm, question);
            if (raw > 0) {
                scored.add(new ScoredKeywordChunk(chunk, raw, 0.0));
            }
        }

        scored.sort(Comparator.comparingDouble(ScoredKeywordChunk::rawScore).reversed());
        int topM = effectiveKeywordTopM(signals);
        List<ScoredKeywordChunk> top = scored.size() > topM ? scored.subList(0, topM) : scored;

        double maxRaw = top.stream().mapToDouble(ScoredKeywordChunk::rawScore).max().orElse(1.0);
        List<ScoredKeywordChunk> normalizedTop = new ArrayList<>();
        Map<UUID, Double> scoreMap = new LinkedHashMap<>();
        for (ScoredKeywordChunk sc : top) {
            double norm = maxRaw > 0 ? sc.rawScore() / maxRaw : 0.0;
            normalizedTop.add(new ScoredKeywordChunk(sc.chunk(), sc.rawScore(), norm));
            if (sc.chunk().getId() != null) {
                scoreMap.put(sc.chunk().getId(), norm);
            }
        }

        return new KeywordSearchResult(normalizedTop, scoreMap, signals);
    }

    private int effectiveKeywordTopM(QuerySignalExtractor.QuerySignals signals) {
        int configured = Math.max(1, keywordTopM);
        if (signals == null) {
            return configured;
        }
        if (!signals.identifiers().isEmpty() || !signals.dates().isEmpty()) {
            return Math.max(configured, 200);
        }
        return configured;
    }

    public void invalidateIndex(UUID widgetId, String reason) {
        keywordIndexCache.invalidate(widgetId, reason);
    }

    /**
     * Merge vector anchor IDs and keyword hits; dedupe by chunkId.
     */
    public static List<MergedCandidate> mergeCandidates(
            Collection<DocumentChunk> vectorChunks,
            Set<UUID> vectorAnchorIds,
            KeywordSearchResult keywordResult) {
        Map<UUID, MergedCandidate> merged = new LinkedHashMap<>();

        if (vectorChunks != null) {
            for (DocumentChunk c : vectorChunks) {
                if (c.getId() == null) {
                    continue;
                }
                double vScore = vectorAnchorIds != null && vectorAnchorIds.contains(c.getId()) ? 1.0 : 0.0;
                merged.put(c.getId(), new MergedCandidate(c, vScore, 0.0, CandidateSource.VECTOR));
            }
        }

        if (keywordResult != null && keywordResult.topChunks() != null) {
            for (ScoredKeywordChunk kw : keywordResult.topChunks()) {
                DocumentChunk c = kw.chunk();
                if (c.getId() == null) {
                    continue;
                }
                MergedCandidate existing = merged.get(c.getId());
                double kScore = kw.normalizedScore();
                if (existing != null) {
                    double vScore = existing.vectorScore() > 0 ? existing.vectorScore() : 0.0;
                    merged.put(c.getId(), new MergedCandidate(
                            c,
                            vScore,
                            Math.max(existing.keywordScore(), kScore),
                            CandidateSource.BOTH));
                } else {
                    merged.put(c.getId(), new MergedCandidate(c, 0.0, kScore, CandidateSource.KEYWORD));
                }
            }
        }

        return new ArrayList<>(merged.values());
    }

    public static int countBothSource(List<MergedCandidate> merged) {
        if (merged == null) {
            return 0;
        }
        return (int) merged.stream().filter(m -> m.source() == CandidateSource.BOTH).count();
    }

    private KeywordSearchResult emptyResult(String question) {
        QuerySignalExtractor.QuerySignals signals = QuerySignalExtractor.extract(
                question == null ? "" : question);
        return new KeywordSearchResult(List.of(), Map.of(), signals);
    }

    private List<DocumentChunk> loadBoundedCorpus(UUID widgetId) {
        List<DocumentChunk> all = documentChunkRepository
                .findByWidgetConfigIdOrderByDocumentIdAscOrderIndexAsc(widgetId);
        int limit = Math.max(100, maxKeywordScanChunks);
        if (all.size() <= limit) {
            return all;
        }
        return all.subList(0, limit);
    }

    private Map<String, Double> computeCorpusIdf(List<DocumentChunk> corpus,
                                                  QuerySignalExtractor.QuerySignals signals) {
        Set<String> terms = new LinkedHashSet<>(signals.contentTokens());
        for (String id : signals.identifiers()) {
            terms.add(QuerySignalExtractor.normalize(id));
        }
        for (String d : signals.dates()) {
            terms.add(QuerySignalExtractor.normalize(d));
        }
        for (String n : signals.numbers()) {
            terms.add(n);
        }
        if (terms.isEmpty()) {
            return Map.of();
        }
        int n = corpus.size();
        Map<String, Double> idf = new LinkedHashMap<>();
        for (String term : terms) {
            String t = QuerySignalExtractor.normalize(term);
            if (t.isBlank()) {
                continue;
            }
            long df = corpus.stream().filter(c -> haystack(c).contains(t)).count();
            idf.put(t, Math.log((n + 1.0) / (df + 1.0)) + 1.0);
        }
        return idf;
    }

    double scoreChunk(DocumentChunk chunk,
                      QuerySignalExtractor.QuerySignals signals,
                      Map<String, Double> idfByTerm,
                      String question) {
        String haystack = haystack(chunk);
        String haystackRaw = rawHaystack(chunk);
        if (haystack.isBlank()) {
            return 0.0;
        }

        double score = 0.0;

        for (String id : signals.identifiers()) {
            if (containsIgnoreCase(haystackRaw, id)) {
                score += exactMatchBoost * (1.0 + rarityBoost(id, idfByTerm));
            }
        }

        for (String date : signals.dates()) {
            if (containsIgnoreCase(haystackRaw, date)) {
                score += exactMatchBoost;
            }
        }

        for (String label : signals.structuredLabels()) {
            String normLabel = QuerySignalExtractor.normalize(label);
            if (!"normalized_table_row".equals(chunk.getChunkType())) {
                if (haystack.contains(normLabel)) {
                    score += exactMatchBoost;
                }
                score += structuredLabelNumberMatch(haystack, normLabel);
            }
        }

        for (String ngram : signals.ngrams()) {
            if (haystack.contains(ngram)) {
                score += phraseMatchBoost * Math.min(3.0, ngram.split("\\s+").length * 0.5);
            }
        }

        for (Map.Entry<String, Double> entry : idfByTerm.entrySet()) {
            if (haystack.contains(entry.getKey())) {
                score += entry.getValue();
            }
        }

        score += fieldOverlapBoost(chunk, signals);
        score += chunkTypeBoost(chunk, signals, question);
        score += proximityBoost(haystack, signals);
        score += dateRangeIntentBoost(haystack, question);
        score -= textTableMegaPenalty(chunk, signals, question);
        score += cellAwareBoost(chunk, signals, question);

        return score;
    }

    private double cellAwareBoost(DocumentChunk chunk,
                                  QuerySignalExtractor.QuerySignals signals,
                                  String question) {
        if (!"normalized_table_row".equals(chunk.getChunkType())) {
            return 0.0;
        }
        CellAwareTableRowScorer.CellAwareScore cellScore =
                CellAwareTableRowScorer.score(chunk, signals, question);
        return cellScore.total();
    }

    private double textTableMegaPenalty(DocumentChunk chunk,
                                        QuerySignalExtractor.QuerySignals signals,
                                        String question) {
        if (!isTableLikeQuery(signals, question)) {
            return 0.0;
        }
        String type = chunk.getChunkType() == null ? "text" : chunk.getChunkType();
        if (!"text".equals(type)) {
            return 0.0;
        }
        String content = Optional.ofNullable(chunk.getContent()).orElse("");
        if (content.length() < 800) {
            return 0.0;
        }
        if (NormalizedTableService.hasHighTableLikeDensity(content)) {
            return tableRowBoost * 1.5;
        }
        return 0.0;
    }

    static boolean isTableLikeQuery(QuerySignalExtractor.QuerySignals signals, String question) {
        if (signals == null) {
            return false;
        }
        if (!signals.identifiers().isEmpty()
                || !signals.structuredLabels().isEmpty()
                || !signals.dates().isEmpty()) {
            return true;
        }
        return signals.numbers().size() >= 2;
    }

    static boolean isTableLikeQuestion(String question) {
        QuerySignalExtractor.QuerySignals signals = QuerySignalExtractor.extract(question);
        return isTableLikeQuery(signals, question);
    }

    private double dateRangeIntentBoost(String haystack, String question) {
        QuerySignalExtractor.QuerySignals signals = QuerySignalExtractor.extract(question);
        if (signals.dates().size() >= 2) {
            return signals.dates().stream()
                    .filter(d -> containsIgnoreCase(haystack, d))
                    .count() >= 2 ? exactMatchBoost * 1.5 : 0.0;
        }
        java.util.regex.Matcher dates = java.util.regex.Pattern.compile(
                "\\d{1,4}[/\\-.]\\d{1,2}(?:[/\\-.]\\d{1,4})?").matcher(haystack);
        int found = 0;
        while (dates.find()) {
            found++;
            if (found >= 2) {
                return exactMatchBoost;
            }
        }
        return 0.0;
    }

    private double structuredLabelNumberMatch(String haystack, String normLabel) {
        CellAwareTableRowScorer.ParsedStructuredLabel label = CellAwareTableRowScorer.parseLabel(normLabel);
        if (label == null) {
            return 0.0;
        }
        String prefix = QuerySignalExtractor.normalize(label.type());
        String value = QuerySignalExtractor.normalize(label.value());
        String compact = prefix.replace(" ", "") + value;
        String acronym = acronym(prefix) + value;
        if ((haystack.contains(normLabel) && CellAwareTableRowScorer.valueMatchesBoundary(haystack, "", value))
                || haystack.contains(compact)
                || haystack.contains(acronym)) {
            return exactMatchBoost;
        }
        return 0.0;
    }

    private String acronym(String normalizedPrefix) {
        StringBuilder sb = new StringBuilder();
        for (String token : normalizedPrefix.split("\\s+")) {
            if (!token.isBlank()) {
                sb.append(token.charAt(0));
            }
        }
        return sb.toString();
    }

    private double rarityBoost(String term, Map<String, Double> idfByTerm) {
        String t = QuerySignalExtractor.normalize(term);
        return idfByTerm.getOrDefault(t, 1.0) * 0.1;
    }

    private double fieldOverlapBoost(DocumentChunk chunk, QuerySignalExtractor.QuerySignals signals) {
        double boost = 0.0;
        String sectionTitle = QuerySignalExtractor.normalize(
                Optional.ofNullable(chunk.getSectionTitle()).orElse(""));
        String heading = QuerySignalExtractor.normalize(
                Optional.ofNullable(chunk.getHeadingPathText()).orElse(""));

        for (String token : signals.contentTokens()) {
            if (sectionTitle.contains(token)) {
                boost += 0.5;
            } else if (heading.contains(token)) {
                boost += 0.3;
            }
        }
        return boost;
    }

    private double chunkTypeBoost(DocumentChunk chunk,
                                  QuerySignalExtractor.QuerySignals signals,
                                  String question) {
        String type = chunk.getChunkType() == null ? "text" : chunk.getChunkType();
        boolean tableLikeQuery = isTableLikeQuery(signals, question);

        if ("normalized_table_row".equals(type) && tableLikeQuery) {
            return tableRowBoost * 1.8;
        }
        if ("table_row_group".equals(type) && tableLikeQuery) {
            return tableRowBoost;
        }
        if ("table_summary".equals(type) && tableLikeQuery) {
            return tableSummaryBoost;
        }
        return 0.0;
    }

    private double proximityBoost(String haystack, QuerySignalExtractor.QuerySignals signals) {
        if (signals.identifiers().isEmpty() || signals.structuredLabels().isEmpty()) {
            return 0.0;
        }
        for (String id : signals.identifiers()) {
            String normId = QuerySignalExtractor.normalize(id);
            int idPos = haystack.indexOf(normId);
            if (idPos < 0) {
                continue;
            }
            for (String label : signals.structuredLabels()) {
                String normLabel = QuerySignalExtractor.normalize(label);
                int labelPos = haystack.indexOf(normLabel);
                if (labelPos >= 0 && Math.abs(idPos - labelPos) < 80) {
                    return 0.5;
                }
            }
        }
        return 0.0;
    }

    static String haystack(DocumentChunk chunk) {
        String content = QuerySignalExtractor.normalize(Optional.ofNullable(chunk.getContent()).orElse(""));
        String heading = QuerySignalExtractor.normalize(Optional.ofNullable(chunk.getHeadingPathText()).orElse(""));
        String sectionTitle = QuerySignalExtractor.normalize(Optional.ofNullable(chunk.getSectionTitle()).orElse(""));
        return (sectionTitle + " " + heading + " " + content).trim();
    }

    private static String rawHaystack(DocumentChunk chunk) {
        String content = Optional.ofNullable(chunk.getContent()).orElse("");
        String heading = Optional.ofNullable(chunk.getHeadingPathText()).orElse("");
        String sectionTitle = Optional.ofNullable(chunk.getSectionTitle()).orElse("");
        return (sectionTitle + " " + heading + " " + content);
    }

    private static boolean containsIgnoreCase(String haystack, String needle) {
        if (needle == null || needle.isBlank()) {
            return false;
        }
        return haystack.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT));
    }
}
