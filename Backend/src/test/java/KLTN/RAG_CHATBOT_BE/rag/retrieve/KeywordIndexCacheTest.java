package KLTN.RAG_CHATBOT_BE.rag.retrieve;

import KLTN.RAG_CHATBOT_BE.domain.document.DocumentChunk;
import KLTN.RAG_CHATBOT_BE.domain.document.DocumentChunkRepository;
import KLTN.RAG_CHATBOT_BE.rag.analysis.QuerySignalExtractor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KeywordIndexCacheTest {

    private static final UUID WIDGET_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");

    @Mock
    private DocumentChunkRepository documentChunkRepository;

    private KeywordIndexCache cache;

    @BeforeEach
    void setUp() {
        cache = new KeywordIndexCache(documentChunkRepository);
    }

    @Test
    void repeatedLookup_doesNotRebuildIndex() {
        DocumentChunk chunk = DocumentChunk.builder()
                .id(UUID.randomUUID())
                .content("Kỹ năng mềm Nhóm 4 B301")
                .build();
        when(documentChunkRepository.findByWidgetConfigIdOrderByDocumentIdAscOrderIndexAsc(WIDGET_ID))
                .thenReturn(List.of(chunk));

        QuerySignalExtractor.QuerySignals signals = QuerySignalExtractor.extract("Kỹ năng mềm Nhóm 4");
        KeywordIndexCache.LookupResult first = cache.lookup(WIDGET_ID, signals, 500);
        KeywordIndexCache.LookupResult second = cache.lookup(WIDGET_ID, signals, 500);

        assertTrue(first.available());
        assertTrue(second.available());
        assertEquals(0, second.buildMs(), "second lookup should reuse cached index");
        verify(documentChunkRepository, times(1))
                .findByWidgetConfigIdOrderByDocumentIdAscOrderIndexAsc(WIDGET_ID);
    }

    @Test
    void invalidate_forcesRebuildOnNextLookup() {
        DocumentChunk chunk = DocumentChunk.builder()
                .id(UUID.randomUUID())
                .content("sample")
                .build();
        when(documentChunkRepository.findByWidgetConfigIdOrderByDocumentIdAscOrderIndexAsc(WIDGET_ID))
                .thenReturn(List.of(chunk));

        QuerySignalExtractor.QuerySignals signals = QuerySignalExtractor.extract("sample");
        cache.lookup(WIDGET_ID, signals, 500);
        cache.invalidate(WIDGET_ID, "test");
        cache.lookup(WIDGET_ID, signals, 500);

        verify(documentChunkRepository, times(2))
                .findByWidgetConfigIdOrderByDocumentIdAscOrderIndexAsc(WIDGET_ID);
    }

    @Test
    void warm_buildsIndexWithoutLookupSignals() {
        DocumentChunk chunk = DocumentChunk.builder()
                .id(UUID.randomUUID())
                .content("warm index")
                .build();
        when(documentChunkRepository.findByWidgetConfigIdOrderByDocumentIdAscOrderIndexAsc(WIDGET_ID))
                .thenReturn(List.of(chunk));

        cache.warm(WIDGET_ID, 500);
        QuerySignalExtractor.QuerySignals signals = QuerySignalExtractor.extract("warm index");
        KeywordIndexCache.LookupResult lookup = cache.lookup(WIDGET_ID, signals, 500);

        assertEquals(0, lookup.buildMs(), "warm should pre-build index for subsequent lookup");
        verify(documentChunkRepository, times(1))
                .findByWidgetConfigIdOrderByDocumentIdAscOrderIndexAsc(WIDGET_ID);
    }
}
