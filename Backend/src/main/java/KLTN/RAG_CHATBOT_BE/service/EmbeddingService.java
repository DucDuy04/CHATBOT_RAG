// package KLTN.RAG_CHATBOT_BE.service;

// import dev.langchain4j.data.embedding.Embedding;
// import dev.langchain4j.data.segment.TextSegment;
// import dev.langchain4j.model.embedding.EmbeddingModel;
// import dev.langchain4j.model.output.Response;
// import dev.langchain4j.store.embedding.EmbeddingMatch;
// import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
// import dev.langchain4j.store.embedding.qdrant.QdrantEmbeddingStore;
// import lombok.RequiredArgsConstructor;
// import lombok.extern.slf4j.Slf4j;
// import org.springframework.stereotype.Service;
// import java.util.ArrayList;
// import java.util.List;
// import java.util.Map;

// @Slf4j
// @Service
// @RequiredArgsConstructor
// public class EmbeddingService {

//     private final EmbeddingModel embeddingModel;
//     private final QdrantEmbeddingStore qdrantEmbeddingStore;

//     // Embed và lưu tất cả chunk vào Qdrant
//     public void embedAndStore(List<String> chunks, Long documentId, String fileName) {
//         log.info("Bắt đầu embed {} chunks cho document id={}", chunks.size(), documentId);

//         // Tạo tất cả TextSegment kèm metadata
//         List<TextSegment> segments = new ArrayList<>();
//         for (int i = 0; i < chunks.size(); i++) {
//             segments.add(TextSegment.from(
//                     chunks.get(i),
//                     dev.langchain4j.data.document.Metadata.from(Map.of(
//                             "documentId", documentId.toString(),
//                             "fileName", fileName,
//                             "chunkIndex", String.valueOf(i)))));
//         }

//         // Gọi API một lần duy nhất cho toàn bộ chunks
//         Response<List<Embedding>> response = embeddingModel.embedAll(segments);
//         List<Embedding> embeddings = response.content();

//         // Lưu tất cả vào Qdrant trong một batch
//         qdrantEmbeddingStore.addAll(embeddings, segments);

//         log.info("Hoàn thành embed {} chunks cho document id={}", chunks.size(), documentId);
//     }

//     // Tìm kiếm các chunk liên quan nhất với câu hỏi
//     public List<EmbeddingMatch<TextSegment>> search(String query, int topK) {
//         // Embed câu hỏi bằng cùng model
//         Embedding queryEmbedding = embeddingModel.embed(TextSegment.from(query)).content();
//         System.out.println("=== QUERY EMBEDDING SIZE: " + queryEmbedding.vectorAsList().size());

//         // Tìm topK vector gần nhất trong Qdrant (cosine similarity)
//         EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
//                 .queryEmbedding(queryEmbedding)
//                 .maxResults(topK)
//                 .build();
//         return qdrantEmbeddingStore.search(request).matches();
//     }
// }

package KLTN.RAG_CHATBOT_BE.service;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingService {

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> qdrantEmbeddingStore;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${qdrant.url:http://localhost:6333}")
    private String qdrantUrl;

    @Value("${qdrant.collection-name:documents}")
    private String collectionName;

    // Dùng khi upload document
    public void embedAndStore(List<String> chunks, Long documentId, String fileName) {
        log.info("Bắt đầu embed {} chunks cho document id={}", chunks.size(), documentId);

        for (int i = 0; i < chunks.size(); i++) {
            String chunk = chunks.get(i);
            Embedding embedding = embeddingModel.embed(TextSegment.from(chunk)).content();

            dev.langchain4j.data.document.Metadata metadata = new dev.langchain4j.data.document.Metadata();
            metadata.put("documentId", documentId.toString());
            metadata.put("fileName", fileName);
            metadata.put("chunkIndex", String.valueOf(i));
            metadata.put("text_segment", chunk);

            TextSegment segment = TextSegment.from(chunk, metadata);
            qdrantEmbeddingStore.add(embedding, segment);
        }

        log.info("Hoàn thành embed {} chunks cho document id={}", chunks.size(), documentId);
    }

    // Dùng khi search — gọi thẳng Qdrant REST API để tránh bug LangChain4j
    public List<TextSegment> search(String query, int topK) {
        Embedding queryEmbedding = embeddingModel.embed(TextSegment.from(query)).content();
        log.info("Query embedding size: {}", queryEmbedding.vectorAsList().size());

        String url = qdrantUrl + "/collections/" + collectionName + "/points/search";

        Map<String, Object> body = new HashMap<>();
        body.put("vector", queryEmbedding.vectorAsList());
        body.put("limit", topK);
        body.put("with_payload", true);
        body.put("with_vector", false);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Map> response = restTemplate.exchange(
                url, HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);

        List<Map<String, Object>> results = (List<Map<String, Object>>) response.getBody().get("result");

        if (results == null || results.isEmpty()) {
            return List.of();
        }

        return results.stream()
                .map(point -> {
                    Map<String, Object> payload = (Map<String, Object>) point.get("payload");
                    String text = (String) payload.getOrDefault("text_segment", "");
                    dev.langchain4j.data.document.Metadata metadata = new dev.langchain4j.data.document.Metadata();
                    payload.forEach((k, v) -> {
                        if (v != null)
                            metadata.put(k, v.toString());
                    });
                    return TextSegment.from(text, metadata);
                })
                .collect(Collectors.toList());
    }
}