package KLTN.RAG_CHATBOT_BE.service;

import KLTN.RAG_CHATBOT_BE.domain.document.Document;
import KLTN.RAG_CHATBOT_BE.domain.document.DocumentChunk;
import KLTN.RAG_CHATBOT_BE.domain.document.DocumentChunkRepository;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KeywordSearchIndexTest {

    @Test
    void indexedRankingMatchesScanRankingForSyntheticCorpus() {
        UUID widgetId = UUID.randomUUID();
        DocumentChunk best = chunk("ABC123 Alpha Group 2 location R101", "normalized_table_row");
        DocumentChunk second = chunk("ABC123 Alpha Group 18 location R202", "normalized_table_row");
        DocumentChunk miss = chunk("DEF456 Beta Group 2 location R303", "normalized_table_row");
        List<DocumentChunk> corpus = List.of(best, second, miss);
        DocumentChunkRepository repo = mock(DocumentChunkRepository.class);
        when(repo.findByWidgetConfigIdOrderByDocumentIdAscOrderIndexAsc(widgetId)).thenReturn(corpus);
        KeywordSearchService service = new KeywordSearchService(repo, new KeywordIndexCache(repo));
        setField(service, "hybridEnabled", true);
        setField(service, "keywordTopM", 10);
        setField(service, "maxKeywordScanChunks", 3000);

        String query = "ABC123 Group 2 location";
        QuerySignalExtractor.QuerySignals signals = QuerySignalExtractor.extract(query);
        List<DocumentChunk> oldScanOrder = corpus.stream()
                .map(c -> new KeywordSearchService.ScoredKeywordChunk(
                        c, service.scoreChunk(c, signals, Map.of(), query), 0))
                .filter(sc -> sc.rawScore() > 0)
                .sorted(Comparator.comparingDouble(KeywordSearchService.ScoredKeywordChunk::rawScore).reversed())
                .map(KeywordSearchService.ScoredKeywordChunk::chunk)
                .toList();

        var indexed = service.search(query, widgetId);

        assertThat(indexed.topChunks()).isNotEmpty();
        assertThat(indexed.topChunks().getFirst().chunk()).isEqualTo(oldScanOrder.getFirst());
    }

    @Test
    void fallbackScanIsUsedWhenIndexUnavailable() {
        UUID widgetId = UUID.randomUUID();
        DocumentChunk match = chunk("ABC123 fallback value", "text");
        DocumentChunkRepository repo = mock(DocumentChunkRepository.class);
        when(repo.findByWidgetConfigIdOrderByDocumentIdAscOrderIndexAsc(widgetId)).thenReturn(List.of(match));
        KeywordSearchService service = new KeywordSearchService(repo, new KeywordIndexCache(null));
        setField(service, "hybridEnabled", true);
        setField(service, "keywordTopM", 10);

        var result = service.search("ABC123", widgetId);

        assertThat(result.topChunks()).hasSize(1);
        assertThat(result.topChunks().getFirst().chunk()).isEqualTo(match);
    }

    @Test
    void noPostingMatchDoesNotFallbackToFullScan() {
        UUID widgetId = UUID.randomUUID();
        DocumentChunk corpus = chunk("ABC123 internal value", "text");
        DocumentChunkRepository repo = mock(DocumentChunkRepository.class);
        when(repo.findByWidgetConfigIdOrderByDocumentIdAscOrderIndexAsc(widgetId)).thenReturn(List.of(corpus));
        KeywordSearchService service = new KeywordSearchService(repo, new KeywordIndexCache(repo));
        setField(service, "hybridEnabled", true);

        var result = service.search("external market rate today", widgetId);

        assertThat(result.topChunks()).isEmpty();
    }

    private static DocumentChunk chunk(String content, String type) {
        return DocumentChunk.builder()
                .id(UUID.randomUUID())
                .document(Document.builder().id(UUID.randomUUID()).build())
                .content(content)
                .chunkType(type)
                .build();
    }

    private static void setField(Object target, String name, Object value) {
        try {
            var field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
