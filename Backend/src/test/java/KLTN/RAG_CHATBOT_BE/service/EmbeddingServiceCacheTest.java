package KLTN.RAG_CHATBOT_BE.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class EmbeddingServiceCacheTest {

    @Test
    void queryEmbeddingCacheKey_normalizesRepeatedSameQuery() {
        String a = EmbeddingService.queryCacheKey("  Alpha   Beta  ", "provider-a", "docs");
        String b = EmbeddingService.queryCacheKey("alpha beta", "provider-a", "docs");
        assertEquals(a, b);
    }

    @Test
    void queryEmbeddingCacheKey_differentProviderMisses() {
        String a = EmbeddingService.queryCacheKey("alpha beta", "provider-a", "docs");
        String b = EmbeddingService.queryCacheKey("alpha beta", "provider-b", "docs");
        assertNotEquals(a, b);
    }

    @Test
    void queryEmbeddingCacheKey_differentCollectionMisses() {
        String a = EmbeddingService.queryCacheKey("alpha beta", "provider-a", "docs-a");
        String b = EmbeddingService.queryCacheKey("alpha beta", "provider-a", "docs-b");
        assertNotEquals(a, b);
    }
}
