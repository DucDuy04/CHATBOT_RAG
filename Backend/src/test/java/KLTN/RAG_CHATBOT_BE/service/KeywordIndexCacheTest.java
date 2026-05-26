package KLTN.RAG_CHATBOT_BE.service;

import KLTN.RAG_CHATBOT_BE.domain.document.Document;
import KLTN.RAG_CHATBOT_BE.domain.document.DocumentChunk;
import KLTN.RAG_CHATBOT_BE.domain.document.DocumentChunkRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KeywordIndexCacheTest {

    @Test
    void indexBuildIncludesActiveChunksOnly() {
        UUID widgetId = UUID.randomUUID();
        DocumentChunk active = chunk("ABC123 active value", "text");
        DocumentChunk deleted = chunk("ZZZ999 deleted value", "text");
        deleted.setDeletedAt(LocalDateTime.now());

        var index = KeywordIndexCache.WidgetKeywordIndex.build(widgetId, List.of(active, deleted));

        var activeLookup = index.selectCandidates(QuerySignalExtractor.extract("ABC123"));
        var deletedLookup = index.selectCandidates(QuerySignalExtractor.extract("ZZZ999"));

        assertThat(activeLookup.candidates()).containsExactly(active);
        assertThat(deletedLookup.candidates()).isEmpty();
    }

    @Test
    void tokenPostingLookupReturnsExpectedCandidate() {
        UUID widgetId = UUID.randomUUID();
        DocumentChunk alpha = chunk("ABC123 shelf A", "text");
        DocumentChunk beta = chunk("DEF456 shelf B", "text");

        var index = KeywordIndexCache.WidgetKeywordIndex.build(widgetId, List.of(alpha, beta));

        assertThat(index.selectCandidates(QuerySignalExtractor.extract("ABC123")).candidates())
                .containsExactly(alpha);
    }

    @Test
    void identifierQueryRetrievesMatchingChunkWithoutSecondRepositoryLoad() {
        UUID widgetId = UUID.randomUUID();
        DocumentChunk match = chunk("Policy ABC123 starts tomorrow", "text");
        DocumentChunk miss = chunk("Policy DEF456 starts later", "text");
        DocumentChunkRepository repo = mock(DocumentChunkRepository.class);
        when(repo.findByWidgetConfigIdOrderByDocumentIdAscOrderIndexAsc(widgetId))
                .thenReturn(List.of(match, miss));

        KeywordIndexCache cache = new KeywordIndexCache(repo);

        var first = cache.lookup(widgetId, QuerySignalExtractor.extract("ABC123"), 3000);
        var second = cache.lookup(widgetId, QuerySignalExtractor.extract("ABC123"), 3000);

        assertThat(first.candidates()).containsExactly(match);
        assertThat(second.candidates()).containsExactly(match);
        verify(repo, times(1)).findByWidgetConfigIdOrderByDocumentIdAscOrderIndexAsc(widgetId);
    }

    @Test
    void dateLikeTermRetrievesMatchingChunk() {
        UUID widgetId = UUID.randomUUID();
        DocumentChunk match = chunk("Window from 01/05/2026 to 05/05/2026", "text");
        DocumentChunk miss = chunk("Window pending", "text");

        var index = KeywordIndexCache.WidgetKeywordIndex.build(widgetId, List.of(match, miss));

        assertThat(index.selectCandidates(QuerySignalExtractor.extract("01/05/2026")).candidates())
                .containsExactly(match);
    }

    @Test
    void multipleTermsUnionCandidates() {
        UUID widgetId = UUID.randomUUID();
        DocumentChunk alpha = chunk("ABC123 first item", "text");
        DocumentChunk beta = chunk("DEF456 second item", "text");
        DocumentChunk miss = chunk("plain item", "text");

        var index = KeywordIndexCache.WidgetKeywordIndex.build(widgetId, List.of(alpha, beta, miss));

        assertThat(index.selectCandidates(QuerySignalExtractor.extract("ABC123 DEF456")).candidates())
                .containsExactly(alpha, beta);
    }

    @Test
    void cacheInvalidationRebuildsNextLookup() {
        UUID widgetId = UUID.randomUUID();
        DocumentChunk first = chunk("ABC123 first", "text");
        DocumentChunk second = chunk("ABC123 second", "text");
        DocumentChunkRepository repo = mock(DocumentChunkRepository.class);
        when(repo.findByWidgetConfigIdOrderByDocumentIdAscOrderIndexAsc(widgetId))
                .thenReturn(List.of(first))
                .thenReturn(List.of(second));

        KeywordIndexCache cache = new KeywordIndexCache(repo);

        assertThat(cache.lookup(widgetId, QuerySignalExtractor.extract("ABC123"), 3000).candidates())
                .containsExactly(first);
        cache.invalidate(widgetId, "test_update");
        assertThat(cache.lookup(widgetId, QuerySignalExtractor.extract("ABC123"), 3000).candidates())
                .containsExactly(second);
        verify(repo, times(2)).findByWidgetConfigIdOrderByDocumentIdAscOrderIndexAsc(widgetId);
    }

    @Test
    void cacheKeySeparatesWidgets() {
        UUID widgetA = UUID.randomUUID();
        UUID widgetB = UUID.randomUUID();
        DocumentChunk a = chunk("ABC123 in A", "text");
        DocumentChunk b = chunk("ABC123 in B", "text");
        DocumentChunkRepository repo = mock(DocumentChunkRepository.class);
        when(repo.findByWidgetConfigIdOrderByDocumentIdAscOrderIndexAsc(widgetA)).thenReturn(List.of(a));
        when(repo.findByWidgetConfigIdOrderByDocumentIdAscOrderIndexAsc(widgetB)).thenReturn(List.of(b));

        KeywordIndexCache cache = new KeywordIndexCache(repo);

        assertThat(cache.lookup(widgetA, QuerySignalExtractor.extract("ABC123"), 3000).candidates())
                .containsExactly(a);
        assertThat(cache.lookup(widgetB, QuerySignalExtractor.extract("ABC123"), 3000).candidates())
                .containsExactly(b);
    }

    @Test
    void unavailableRepositorySignalsFallback() {
        KeywordIndexCache cache = new KeywordIndexCache(null);

        var lookup = cache.lookup(UUID.randomUUID(), QuerySignalExtractor.extract("ABC123"), 3000);

        assertThat(lookup.available()).isFalse();
        assertThat(lookup.unavailableReason()).isEqualTo("no_repository");
    }

    @Test
    void oosQueryCanReturnEmptyCandidatesWithoutFabricatingMatch() {
        UUID widgetId = UUID.randomUUID();
        DocumentChunk corpus = chunk("Internal policy ABC123 only", "text");

        var index = KeywordIndexCache.WidgetKeywordIndex.build(widgetId, List.of(corpus));

        assertThat(index.selectCandidates(QuerySignalExtractor.extract("external market rate today")).candidates())
                .isEmpty();
    }

    private static DocumentChunk chunk(String content, String type) {
        return DocumentChunk.builder()
                .id(UUID.randomUUID())
                .document(Document.builder().id(UUID.randomUUID()).build())
                .content(content)
                .chunkType(type)
                .build();
    }
}
