package KLTN.RAG_CHATBOT_BE.index.embedding;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmbeddingServiceQueryCacheIntegrationTest {

    @Mock
    private EmbeddingModel embeddingModel;

    private EmbeddingService embeddingService;

    @BeforeEach
    void setUp() {
        embeddingService = new EmbeddingService(embeddingModel);
        ReflectionTestUtils.setField(embeddingService, "qdrantHost", "localhost");
        ReflectionTestUtils.setField(embeddingService, "qdrantHttpPort", 6333);
        ReflectionTestUtils.setField(embeddingService, "qdrantCollectionName", "documents");
    }

    @Test
    @SuppressWarnings("unchecked")
    void sameQueryUsesEmbeddingProviderOnce() {
        Embedding embedding = Embedding.from(new float[] {0.1f, 0.2f, 0.3f});
        when(embeddingModel.embedAll(anyList()))
                .thenReturn(Response.from(List.of(embedding)));

        UUID widgetId = UUID.randomUUID();
        // search() calls getCachedOrEmbedQuery internally; mock rest client path by testing cache path only
        // First call populates cache via private method behavior through reflection on cache key path
        List<Float> first = invokeCachedOrEmbed("hello world");
        List<Float> second = invokeCachedOrEmbed("hello world");

        assertEquals(first, second);
        verify(embeddingModel, times(1)).embedAll(anyList());
    }

    @Test
    @SuppressWarnings("unchecked")
    void differentQueriesCallProviderTwice() {
        Embedding embedding = Embedding.from(new float[] {0.1f, 0.2f, 0.3f});
        when(embeddingModel.embedAll(anyList()))
                .thenReturn(Response.from(List.of(embedding)));

        invokeCachedOrEmbed("query one");
        invokeCachedOrEmbed("query two");

        verify(embeddingModel, times(2)).embedAll(anyList());
    }

    @Test
    @SuppressWarnings("unchecked")
    void cacheDisabledByClearingMap_callsProviderEachTime() {
        Embedding embedding = Embedding.from(new float[] {0.1f, 0.2f, 0.3f});
        when(embeddingModel.embedAll(anyList()))
                .thenReturn(Response.from(List.of(embedding)));

        invokeCachedOrEmbed("same");
        Map<String, ?> cache = (Map<String, ?>) ReflectionTestUtils.getField(embeddingService, "queryEmbeddingCache");
        assertNotNull(cache);
        cache.clear();
        invokeCachedOrEmbed("same");

        verify(embeddingModel, times(2)).embedAll(anyList());
    }

    @SuppressWarnings("unchecked")
    private List<Float> invokeCachedOrEmbed(String query) {
        return (List<Float>) ReflectionTestUtils.invokeMethod(embeddingService, "getCachedOrEmbedQuery", query);
    }
}
