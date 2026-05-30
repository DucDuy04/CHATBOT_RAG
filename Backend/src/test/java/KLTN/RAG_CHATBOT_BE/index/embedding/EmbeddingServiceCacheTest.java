package KLTN.RAG_CHATBOT_BE.index.embedding;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EmbeddingServiceCacheTest {

    @Test
    void queryCacheKey_normalizesWhitespaceAndCase() {
        String a = EmbeddingService.queryCacheKey("  Hello   World  ", "provider", "docs");
        String b = EmbeddingService.queryCacheKey("hello world", "provider", "docs");
        assertEquals(a, b);
    }

    @Test
    void queryCacheKey_differsByProviderAndCollection() {
        String base = EmbeddingService.queryCacheKey("q", "p1", "c1");
        assertNotEquals(base, EmbeddingService.queryCacheKey("q", "p2", "c1"));
        assertNotEquals(base, EmbeddingService.queryCacheKey("q", "p1", "c2"));
    }

    @Test
    void queryCacheKey_handlesNullQuery() {
        String key = EmbeddingService.queryCacheKey(null, "p", "c");
        assertTrue(key.contains("|"));
        assertDoesNotThrow(() -> EmbeddingService.queryCacheKey(null, "p", "c"));
    }
}
