package KLTN.RAG_CHATBOT_BE.service;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.qdrant.QdrantEmbeddingStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingService {

    private final EmbeddingModel embeddingModel;
    private final QdrantEmbeddingStore qdrantEmbeddingStore;

    // Embed và lưu tất cả chunk vào Qdrant
    public void embedAndStore(List<String> chunks, Long documentId, String fileName) {
        log.info("Bắt đầu embed {} chunks cho document id={}", chunks.size(), documentId);

        // Tạo tất cả TextSegment kèm metadata
        List<TextSegment> segments = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            segments.add(TextSegment.from(
                    chunks.get(i),
                    dev.langchain4j.data.document.Metadata.from(Map.of(
                            "documentId", documentId.toString(),
                            "fileName", fileName,
                            "chunkIndex", String.valueOf(i)))));
        }

        // Gọi API một lần duy nhất cho toàn bộ chunks
        Response<List<Embedding>> response = embeddingModel.embedAll(segments);
        List<Embedding> embeddings = response.content();

        // Lưu tất cả vào Qdrant trong một batch
        qdrantEmbeddingStore.addAll(embeddings, segments);

        log.info("Hoàn thành embed {} chunks cho document id={}", chunks.size(), documentId);
    }

    // Tìm kiếm các chunk liên quan nhất với câu hỏi
    public List<EmbeddingMatch<TextSegment>> search(String query, int topK) {
        // Embed câu hỏi bằng cùng model
        Embedding queryEmbedding = embeddingModel.embed(query).content();

        // Tìm topK vector gần nhất trong Qdrant (cosine similarity)
        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(topK)
                .build();
        return qdrantEmbeddingStore.search(request).matches();
    }
}