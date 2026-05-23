package KLTN.RAG_CHATBOT_BE.service;

import KLTN.RAG_CHATBOT_BE.domain.document.DocumentChunk;
import KLTN.RAG_CHATBOT_BE.domain.document.DocumentChunkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@RequiredArgsConstructor
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

    public boolean isHybridEnabled() {
        return hybridEnabled;
    }

    public KeywordSearchResult search(String question, UUID widgetId) {
        if (!hybridEnabled || question == null || question.isBlank() || widgetId == null) {
            return emptyResult(question);
        }

        QuerySignalExtractor.QuerySignals signals = QuerySignalExtractor.extract(question);
        List<DocumentChunk> corpus = loadBoundedCorpus(widgetId);
        if (corpus.isEmpty()) {
            log.info("[RAG][hybrid] keywordCandidates=0 (empty corpus) widgetId={}", widgetId);
            return new KeywordSearchResult(List.of(), Map.of(), signals);
        }

        Map<String, Double> idfByTerm = computeCorpusIdf(corpus, signals);
        List<ScoredKeywordChunk> scored = new ArrayList<>();
        for (DocumentChunk chunk : corpus) {
            double raw = scoreChunk(chunk, signals, idfByTerm, question);
            if (raw > 0) {
                scored.add(new ScoredKeywordChunk(chunk, raw, 0.0));
            }
        }

        scored.sort(Comparator.comparingDouble(ScoredKeywordChunk::rawScore).reversed());
        int topM = Math.max(1, keywordTopM);
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

        log.info("[RAG][hybrid] keywordSignals identifiers={} dates={} numbers={} labels={} ngrams={} "
                        + "corpusScanned={} keywordCandidates={}",
                signals.identifiers().size(), signals.dates().size(), signals.numbers().size(),
                signals.structuredLabels().size(), signals.ngrams().size(),
                corpus.size(), normalizedTop.size());

        return new KeywordSearchResult(normalizedTop, scoreMap, signals);
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
            if (haystack.contains(normLabel)) {
                score += exactMatchBoost;
            }
            score += structuredLabelNumberMatch(haystack, normLabel);
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

        return score;
    }

    private double dateRangeIntentBoost(String haystack, String question) {
        String q = QuerySignalExtractor.normalize(question);
        boolean rangeIntent = (q.contains("tu") && q.contains("den"))
                || q.contains("khi nao")
                || (q.contains("ngay") && q.contains("nao"));
        if (!rangeIntent) {
            return 0.0;
        }
        java.util.regex.Pattern rangePattern = java.util.regex.Pattern.compile(
                "\\d{1,2}[/\\-.]\\d{1,2}[/\\-.]\\d{2,4}\\s*(den|to|-)\\s*\\d{1,2}");
        return rangePattern.matcher(haystack).find() ? exactMatchBoost * 1.5 : 0.0;
    }

    private double structuredLabelNumberMatch(String haystack, String normLabel) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+[a-z]?)").matcher(normLabel);
        if (!m.find()) {
            return 0.0;
        }
        String num = m.group(1);
        String[] prefixes = {"hk", "ky", "nhom", "group", "lop", "class", "phong", "room"};
        for (String prefix : prefixes) {
            if (haystack.contains(prefix + num) || haystack.contains(prefix + " " + num)) {
                return exactMatchBoost;
            }
        }
        return 0.0;
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
        boolean tableLikeQuery = signals.identifiers().size() > 0
                || signals.structuredLabels().size() > 0
                || (question != null && question.toLowerCase(Locale.ROOT).matches(".*\\b(bang|table|cell|hang|cot|row|column)\\b.*"));

        if ("table_row_group".equals(type) && tableLikeQuery) {
            return tableRowBoost;
        }
        if ("table_summary".equals(type)) {
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
