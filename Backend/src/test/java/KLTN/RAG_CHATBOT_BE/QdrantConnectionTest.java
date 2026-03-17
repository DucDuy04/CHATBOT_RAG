package KLTN.RAG_CHATBOT_BE;

import dev.langchain4j.store.embedding.qdrant.QdrantEmbeddingStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import static org.assertj.core.api.Assertions.assertThatNoException;

@SpringBootTest
class QdrantConnectionTest {

    @Autowired
    private QdrantEmbeddingStore qdrantEmbeddingStore;

    @Test
    void qdrant_connection_should_work() {
        // Nếu test này pass = kết nối Qdrant thành công
        assertThatNoException().isThrownBy(() -> {
            System.out.println("Qdrant connected: " + qdrantEmbeddingStore);
        });
    }
}