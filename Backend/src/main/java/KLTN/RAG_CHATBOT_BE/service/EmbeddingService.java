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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import dev.langchain4j.data.document.Metadata;
import KLTN.RAG_CHATBOT_BE.record.DocumentChunk;



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

    @Value("${rag.min-score:0.45}")
    private double minScore;

    // --- CẬP NHẬT 1: THÊM WIDGET ID VÀO METADATA KHI LƯU ---
    // Lưu ý: Nhớ sửa chỗ gọi hàm này (VD: DocumentService/VectorStoreService) để truyền thêm widgetId vào nhé!
     public void embedAndStore(List<DocumentChunk> chunks, UUID documentId, String fileName, UUID widgetId) {
        log.info("Bắt đầu chuẩn bị {} chunks cho document id={}, widgetId={}", chunks.size(), documentId, widgetId);

        if (chunks == null || chunks.isEmpty()) {
            log.warn("Không có chunk nào để embed cho document id={}", documentId);
            return;
        }

        // BƯỚC 1: Chuyển đổi toàn bộ DocumentChunk thành TextSegment (Chuẩn của LangChain4j)
        List<TextSegment> segments = new ArrayList<>();
        
        for (int i = 0; i < chunks.size(); i++) {
            DocumentChunk chunk = chunks.get(i);
            
            // Đưa tất cả thông tin vào Metadata để Qdrant lưu trữ dưới dạng Payload
            Metadata metadata = new Metadata()
                .put("documentId", documentId.toString())
                .put("fileName", fileName)
                .put("widgetId", widgetId.toString())
                .put("chunkIndex", i)
                // 👇 THÊM CÁC METADATA QUÝ GIÁ TỪ DOCUMENT CHUNK VÀO ĐÂY
                .put("header", chunk.header())
                .put("startPage", chunk.startPage())
                .put("endPage", chunk.endPage());

            // Lưu ý: Đưa chunk.content() (chính là String) vào TextSegment
            TextSegment segment = TextSegment.from(chunk.content(), metadata);
            segments.add(segment);
        }

        log.info("Bắt đầu nhúng (embed) {} segments...", segments.size());

        // BƯỚC 2: Gọi Embedding Model xử lý BATCH (Hàng loạt). 
        // Cách này cực kỳ nhanh và tối ưu so với việc gọi từng cái trong vòng lặp for.
        List<Embedding> embeddings = embeddingModel.embedAll(segments).content();

        log.info("Lưu {} vector embeddings vào Qdrant...", embeddings.size());

        // BƯỚC 3: Lưu toàn bộ danh sách Vector và TextSegment vào Qdrant trong 1 lần gọi (Batch Insert)
        qdrantEmbeddingStore.addAll(embeddings, segments);

        log.info("Hoàn thành embed và lưu {} chunks cho document id={}", chunks.size(), documentId);
    }

    // --- CẬP NHẬT 2: THÊM BỘ LỌC BẰNG JSON KHI SEARCH REST API ---
    public List<TextSegment> search(String query, int topK, UUID widgetId) {
         log.info("[Search] widgetId={}, query={}", widgetId, query); 
        Embedding queryEmbedding = embeddingModel.embed(TextSegment.from(query)).content();
        log.info("Query embedding size: {}", queryEmbedding.vectorAsList().size());

        String url = qdrantUrl + "/collections/" + collectionName + "/points/search";

        Map<String, Object> body = new HashMap<>();
        body.put("vector", queryEmbedding.vectorAsList());
        body.put("limit", topK);
        body.put("with_payload", true);
        body.put("with_vector", false);

        // THÊM FILTER ĐỂ CHỈ LẤY CÁC VECTOR THUỘC VỀ WIDGET_ID NÀY
        if (widgetId != null) {
            Map<String, Object> matchCondition = new HashMap<>();
            matchCondition.put("value", widgetId.toString());

            Map<String, Object> keyCondition = new HashMap<>();
            keyCondition.put("key", "widgetId");
            keyCondition.put("match", matchCondition);

            Map<String, Object> filterMust = new HashMap<>();
            filterMust.put("must", List.of(keyCondition));

            body.put("filter", filterMust);
            // Cấu trúc JSON sinh ra sẽ giống hệt thế này:
            // "filter": {
            //   "must": [
            //     { "key": "widgetId", "match": { "value": "uuid-cua-widget-o-day" } }
            //   ]
            // }
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Map> response = restTemplate.exchange(
                url, HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);

        List<Map<String, Object>> results = (List<Map<String, Object>>) response.getBody().get("result");

        log.info("[Search] Qdrant trả về {} điểm, sau filter score >= {}", results == null ? 0 : results.size(), minScore);
        if (results == null || results.isEmpty()) {
            return List.of();
        }

        return results.stream()
                .filter(point -> {
                    Object scoreObj = point.get("score");
                    if (!(scoreObj instanceof Number scoreNumber)) {
                        return false;
                    }
                    return scoreNumber.doubleValue() >= minScore;
                })
                .map(point -> {
                    Map<String, Object> payload = (Map<String, Object>) point.get("payload");
                    String text = (String) payload.getOrDefault("text_segment", "");
                    Metadata metadata = new Metadata();
                    payload.forEach((k, v) -> {
                        if (v != null)
                            metadata.put(k, v.toString());
                    });
                    return TextSegment.from(text, metadata);
                })
                .collect(Collectors.toList());
    }
}