package KLTN.RAG_CHATBOT_BE.rag.retrieve;

import KLTN.RAG_CHATBOT_BE.domain.document.DocumentChunk;
import KLTN.RAG_CHATBOT_BE.domain.document.DocumentChunkRepository;
import KLTN.RAG_CHATBOT_BE.rag.analysis.QuerySignalExtractor;
import KLTN.RAG_CHATBOT_BE.audit.metrics.RagLatencyTrace;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class KeywordIndexCache {

    private static final Pattern TOKEN_PATTERN = Pattern.compile("[\\p{L}\\p{N}][\\p{L}\\p{N}/.\\-]{1,80}");
    private static final long DEFAULT_TTL_MS = 60L * 60L * 1000L;
    private static final int DEFAULT_MAX_WIDGETS = 25;
    private static final int MAX_NGRAM_WIDTH = 4;

    private final DocumentChunkRepository documentChunkRepository;
    private final ConcurrentHashMap<UUID, CacheEntry> entries = new ConcurrentHashMap<>();
    private final Object evictionLock = new Object();

    @Value("${rag.retrieval.keyword-index.ttl-ms:3600000}")
    private long ttlMs = DEFAULT_TTL_MS;

    @Value("${rag.retrieval.keyword-index.max-widgets:25}")
    private int maxWidgets = DEFAULT_MAX_WIDGETS;

    public KeywordIndexCache(DocumentChunkRepository documentChunkRepository) {
        this.documentChunkRepository = documentChunkRepository;
    }

    public LookupResult lookup(UUID widgetId, QuerySignalExtractor.QuerySignals signals, int corpusLimit) {
        if (widgetId == null || documentChunkRepository == null) {
            return LookupResult.unavailable("no_repository");
        }
        long[] currentBuildMs = new long[] {0L};
        CacheEntry entry = entries.compute(widgetId, (id, existing) -> {
            if (existing != null && !existing.isExpired(ttlMillis())) {
                existing.touch();
                return existing;
            }
            long started = RagLatencyTrace.now();
            WidgetKeywordIndex index = buildIndex(id, corpusLimit);
            long buildMs = RagLatencyTrace.elapsedMs(started);
            currentBuildMs[0] = buildMs;
            log.info("[RAG][keyword-index] widget={} status={} chunks={} buildMs={} terms={} approxPostings={}",
                    id, existing == null ? "miss" : "expired", index.totalChunks(), buildMs,
                    index.termCount(), index.postingCount());
            return new CacheEntry(index, buildMs);
        });
        evictIfNeeded();
        if (entry == null || entry.index().isEmpty()) {
            return LookupResult.unavailable("empty_index");
        }
        entry.touch();
        long started = RagLatencyTrace.now();
        CandidateSelection selection = entry.index().selectCandidates(signals);
        long lookupMs = RagLatencyTrace.elapsedMs(started);
        return new LookupResult(
                true,
                currentBuildMs[0],
                lookupMs,
                selection.candidates(),
                entry.index().idfByTerm(queryTerms(signals)),
                entry.index().allChunks(),
                selection.termsUsed(),
                selection.broadTermsUsed(),
                selection.candidates().size(),
                null);
    }

    public void invalidate(UUID widgetId, String reason) {
        if (widgetId == null) {
            return;
        }
        entries.remove(widgetId);
        log.info("[RAG][keyword-index] widget={} invalidated reason={}", widgetId, reason);
    }

    /**
     * Pre-build keyword index after ingest so first chat query does not pay index-build latency.
     * Safe no-op when index is already warm and not expired.
     */
    public void warm(UUID widgetId, int corpusLimit) {
        if (widgetId == null || documentChunkRepository == null) {
            return;
        }
        long started = RagLatencyTrace.now();
        CacheEntry entry = entries.compute(widgetId, (id, existing) -> {
            if (existing != null && !existing.isExpired(ttlMillis())) {
                return existing;
            }
            WidgetKeywordIndex index = buildIndex(id, corpusLimit);
            log.info("[RAG][keyword-index] widget={} status=warm chunks={} buildMs={} terms={}",
                    id, index.totalChunks(), RagLatencyTrace.elapsedMs(started), index.termCount());
            return new CacheEntry(index, RagLatencyTrace.elapsedMs(started));
        });
        evictIfNeeded();
        if (entry != null) {
            entry.touch();
        }
    }

    public int cachedWidgetCount() {
        return entries.size();
    }

    private WidgetKeywordIndex buildIndex(UUID widgetId, int corpusLimit) {
        List<DocumentChunk> all = documentChunkRepository
                .findByWidgetConfigIdOrderByDocumentIdAscOrderIndexAsc(widgetId);
        int limit = Math.max(100, corpusLimit);
        List<DocumentChunk> chunks = all.size() <= limit ? all : new ArrayList<>(all.subList(0, limit));
        return WidgetKeywordIndex.build(widgetId, chunks);
    }

    private void evictIfNeeded() {
        int limit = Math.max(1, maxWidgets);
        if (entries.size() <= limit) {
            return;
        }
        synchronized (evictionLock) {
            if (entries.size() <= limit) {
                return;
            }
            entries.entrySet().stream()
                    .sorted(Comparator.comparingLong(e -> e.getValue().lastAccessMs()))
                    .limit(Math.max(0, entries.size() - limit))
                    .map(Map.Entry::getKey)
                    .toList()
                    .forEach(id -> {
                        entries.remove(id);
                        log.info("[RAG][keyword-index] widget={} evicted reason=lru", id);
                    });
        }
    }

    private long ttlMillis() {
        return ttlMs <= 0 ? DEFAULT_TTL_MS : ttlMs;
    }

    private static Set<String> queryTerms(QuerySignalExtractor.QuerySignals signals) {
        LinkedHashSet<String> terms = new LinkedHashSet<>();
        if (signals == null) {
            return terms;
        }
        signals.contentTokens().forEach(t -> addExpandedTerm(terms, t));
        signals.identifiers().forEach(t -> addExpandedTerm(terms, t));
        signals.dates().forEach(t -> addExpandedTerm(terms, t));
        signals.numbers().forEach(t -> addExpandedTerm(terms, t));
        signals.structuredLabels().forEach(t -> addExpandedTerm(terms, t));
        return terms;
    }

    private static void addExpandedTerm(Set<String> out, String value) {
        String normalized = QuerySignalExtractor.normalize(value);
        if (normalized.isBlank()) {
            return;
        }
        out.add(normalized);
        for (String token : tokenize(normalized)) {
            out.add(token);
        }
    }

    private static List<String> tokenize(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        Matcher m = TOKEN_PATTERN.matcher(QuerySignalExtractor.normalize(value));
        LinkedHashSet<String> tokens = new LinkedHashSet<>();
        while (m.find()) {
            String token = trimToken(m.group());
            if (token.length() >= 2) {
                tokens.add(token);
            }
            for (String part : token.split("[/.\\-]+")) {
                if (part.length() >= 2) {
                    tokens.add(part);
                }
            }
        }
        return List.copyOf(tokens);
    }

    private static String trimToken(String token) {
        if (token == null) {
            return "";
        }
        return token.replaceAll("^[/.\\-]+|[/.\\-]+$", "").toLowerCase(Locale.ROOT);
    }

    public record LookupResult(
            boolean available,
            long buildMs,
            long postingLookupMs,
            List<DocumentChunk> candidates,
            Map<String, Double> idfByTerm,
            List<DocumentChunk> corpus,
            int termsUsed,
            int broadTermsUsed,
            int candidateCount,
            String unavailableReason
    ) {
        static LookupResult unavailable(String reason) {
            return new LookupResult(false, 0, 0, List.of(), Map.of(), List.of(), 0, 0, 0, reason);
        }
    }

    private record CacheEntry(WidgetKeywordIndex index, long lastBuildMs, long createdMs, long[] lastAccessMsRef) {
        CacheEntry(WidgetKeywordIndex index, long lastBuildMs) {
            this(index, lastBuildMs, System.currentTimeMillis(), new long[] {System.currentTimeMillis()});
        }

        long lastAccessMs() {
            return lastAccessMsRef[0];
        }

        void touch() {
            lastAccessMsRef[0] = System.currentTimeMillis();
        }

        boolean isExpired(long ttlMs) {
            return System.currentTimeMillis() - createdMs > ttlMs;
        }
    }

    record CandidateSelection(List<DocumentChunk> candidates, int termsUsed, int broadTermsUsed) {}

    static final class WidgetKeywordIndex {
        private final UUID widgetId;
        private final List<DocumentChunk> chunks;
        private final Map<UUID, DocumentChunk> chunksById;
        private final Map<String, Set<UUID>> postings;
        private final Map<String, Integer> documentFrequency;
        private final int postingCount;

        private WidgetKeywordIndex(UUID widgetId,
                                   List<DocumentChunk> chunks,
                                   Map<UUID, DocumentChunk> chunksById,
                                   Map<String, Set<UUID>> postings,
                                   Map<String, Integer> documentFrequency,
                                   int postingCount) {
            this.widgetId = widgetId;
            this.chunks = List.copyOf(chunks);
            this.chunksById = Map.copyOf(chunksById);
            this.postings = Map.copyOf(postings);
            this.documentFrequency = Map.copyOf(documentFrequency);
            this.postingCount = postingCount;
        }

        static WidgetKeywordIndex build(UUID widgetId, List<DocumentChunk> chunks) {
            Map<UUID, DocumentChunk> byId = new LinkedHashMap<>();
            Map<String, Set<UUID>> postings = new HashMap<>();
            int postingCount = 0;
            for (DocumentChunk chunk : chunks == null ? List.<DocumentChunk>of() : chunks) {
                if (chunk.getDeletedAt() != null) {
                    continue;
                }
                UUID chunkId = chunk.getId();
                if (chunkId == null) {
                    continue;
                }
                byId.put(chunkId, chunk);
                Set<String> terms = indexedTerms(chunk);
                for (String term : terms) {
                    postings.computeIfAbsent(term, ignored -> new LinkedHashSet<>()).add(chunkId);
                    postingCount++;
                }
            }
            Map<String, Integer> df = new HashMap<>();
            postings.forEach((term, ids) -> df.put(term, ids.size()));
            List<DocumentChunk> activeChunks = chunks == null
                    ? List.of()
                    : chunks.stream().filter(c -> c.getDeletedAt() == null).toList();
            return new WidgetKeywordIndex(widgetId, activeChunks, byId, postings, df, postingCount);
        }

        boolean isEmpty() {
            return chunks.isEmpty() || postings.isEmpty();
        }

        int totalChunks() {
            return chunks.size();
        }

        int termCount() {
            return postings.size();
        }

        int postingCount() {
            return postingCount;
        }

        List<DocumentChunk> allChunks() {
            return chunks;
        }

        CandidateSelection selectCandidates(QuerySignalExtractor.QuerySignals signals) {
            if (signals == null) {
                return new CandidateSelection(List.of(), 0, 0);
            }
            LinkedHashSet<UUID> ids = new LinkedHashSet<>();
            LinkedHashSet<String> mandatory = new LinkedHashSet<>();
            signals.identifiers().forEach(t -> addExpandedTerm(mandatory, t));
            signals.dates().forEach(t -> addExpandedTerm(mandatory, t));
            int termsUsed = addPostings(ids, mandatory, true);

            List<String> rareTerms = rareQueryTerms(signals, false);
            termsUsed += addPostings(ids, rareTerms, false);

            int broadTermsUsed = 0;
            if (ids.size() < 12) {
                broadTermsUsed = addPostings(ids, rareQueryTerms(signals, true), false);
            }

            List<DocumentChunk> candidates = ids.stream()
                    .map(chunksById::get)
                    .filter(Objects::nonNull)
                    .toList();
            return new CandidateSelection(candidates, termsUsed, broadTermsUsed);
        }

        private List<String> rareQueryTerms(QuerySignalExtractor.QuerySignals signals, boolean broader) {
            LinkedHashSet<String> terms = new LinkedHashSet<>();
            signals.structuredLabels().stream()
                    .map(QuerySignalExtractor::normalize)
                    .filter(t -> !t.isBlank())
                    .forEach(terms::add);
            signals.ngrams().stream()
                    .map(QuerySignalExtractor::normalize)
                    .filter(t -> t.length() >= 4)
                    .forEach(terms::add);
            signals.contentTokens().stream()
                    .map(QuerySignalExtractor::normalize)
                    .filter(t -> t.length() >= 3)
                    .forEach(terms::add);
            int limit = broader ? broadDfLimit() : rareDfLimit();
            int maxTerms = broader ? 8 : 12;
            return terms.stream()
                    .filter(t -> df(t) <= limit)
                    .sorted(Comparator.comparingInt(this::df))
                    .limit(maxTerms)
                    .toList();
        }

        Map<String, Double> idfByTerm(Collection<String> terms) {
            if (terms == null || terms.isEmpty()) {
                return Map.of();
            }
            int n = Math.max(1, chunks.size());
            Map<String, Double> out = new LinkedHashMap<>();
            for (String term : terms) {
                String normalized = QuerySignalExtractor.normalize(term);
                if (normalized.isBlank()) {
                    continue;
                }
                int df = documentFrequency.getOrDefault(normalized, 0);
                out.put(normalized, Math.log((n + 1.0) / (df + 1.0)) + 1.0);
            }
            return out;
        }

        private int addPostings(Set<UUID> out, Collection<String> terms, boolean allowBroad) {
            int used = 0;
            for (String term : terms) {
                String normalized = QuerySignalExtractor.normalize(term);
                if (normalized.isBlank()) {
                    continue;
                }
                Set<UUID> ids = postings.get(normalized);
                if (ids == null || ids.isEmpty()) {
                    continue;
                }
                if (!allowBroad && isTooBroad(normalized, ids.size())) {
                    continue;
                }
                out.addAll(ids);
                used++;
            }
            return used;
        }

        private boolean isTooBroad(String term, int df) {
            if (term.length() >= 8 && term.matches(".*\\d.*")) {
                return false;
            }
            return df > broadDfLimit();
        }

        private int rareDfLimit() {
            return Math.max(25, Math.min(350, (int) Math.ceil(chunks.size() * 0.12)));
        }

        private int broadDfLimit() {
            return Math.max(40, Math.min(800, (int) Math.ceil(chunks.size() * 0.25)));
        }

        private int df(String term) {
            return Optional.ofNullable(documentFrequency.get(term)).orElse(Integer.MAX_VALUE);
        }

        private static Set<String> indexedTerms(DocumentChunk chunk) {
            LinkedHashSet<String> terms = new LinkedHashSet<>();
            addTextTerms(terms, chunk.getSectionTitle());
            addTextTerms(terms, chunk.getHeadingPathText());
            addTextTerms(terms, chunk.getContent());
            addTextTerms(terms, chunk.getCellsJson());
            addTextTerms(terms, chunk.getTableName());
            addTextTerms(terms, chunk.getGroupContext());
            return terms;
        }

        private static void addTextTerms(Set<String> terms, String text) {
            List<String> tokens = tokenize(text);
            terms.addAll(tokens);
            int maxWidth = Math.min(MAX_NGRAM_WIDTH, tokens.size());
            for (int width = 2; width <= maxWidth; width++) {
                for (int i = 0; i <= tokens.size() - width; i++) {
                    String gram = String.join(" ", tokens.subList(i, i + width));
                    if (gram.length() >= 4) {
                        terms.add(gram);
                    }
                }
            }
        }
    }
}
