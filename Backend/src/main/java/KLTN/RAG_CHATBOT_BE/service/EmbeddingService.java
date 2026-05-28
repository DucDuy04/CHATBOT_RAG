package KLTN.RAG_CHATBOT_BE.service;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingService {

    private final EmbeddingModel embeddingModel;
    private final RestClient restClient = RestClient.create();

    /** Number of Qdrant points per REST upsert batch (kept low for low-RAM production). */
    private static final int QDRANT_UPSERT_BATCH_SIZE = 100;

    private static final long QUERY_EMBEDDING_CACHE_TTL_MS = 60L * 60L * 1000L;
    private static final int QUERY_EMBEDDING_CACHE_MAX_SIZE = 1000;

    private final Map<String, CachedEmbedding> queryEmbeddingCache = Collections.synchronizedMap(
            new LinkedHashMap<>(256, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, CachedEmbedding> eldest) {
                    return size() > QUERY_EMBEDDING_CACHE_MAX_SIZE;
                }
            });

    @Value("${qdrant.host}")
    private String qdrantHost;

    @Value("${qdrant.http-port}")
    private int qdrantHttpPort;

    @Value("${qdrant.collection-name}")
    private String qdrantCollectionName;

    public void embedAndStore(
            List<KLTN.RAG_CHATBOT_BE.domain.document.DocumentChunk> chunks,
            UUID documentId,
            String fileName,
            UUID widgetId
    ) {
        log.info("Bắt đầu embed {} chunks cho document={}, widget={}", chunks.size(), documentId, widgetId);

        if (chunks == null || chunks.isEmpty()) {
            return;
        }

        List<TextSegment> segments = new ArrayList<>();

        for (KLTN.RAG_CHATBOT_BE.domain.document.DocumentChunk chunk : chunks) {
            Metadata metadata = new Metadata()
                    .put("chunk_id", chunk.getId().toString())
                    .put("document_id", documentId.toString())
                    .put("documentId", documentId.toString())
                    .put("fileName", fileName)
                    .put("source_file", fileName)
                    .put("widgetId", widgetId.toString())
                    .put("chunkIndex", safeInt(chunk.getChunkIndex()))
                    .put("chunk_type", safeString(chunk.getChunkType()))
                    .put("section_id", safeString(chunk.getSectionId()))
                    .put("parent_id", safeString(chunk.getParentId()))
                    .put("section_title", safeString(chunk.getSectionTitle()))
                    .put("heading_path_text", safeString(chunk.getHeadingPathText()))
                    .put("page_start", safeInt(chunk.getPageStart()))
                    .put("page_end", safeInt(chunk.getPageEnd()))
                    .put("order_index", safeInt(chunk.getOrderIndex()))
                    // section_order: thứ tự section trong tài liệu — cho phép sort lại đúng thứ tự gốc khi reconstruct context
                    .put("section_order", safeInt(chunk.getSectionOrder()))
                    // heading_level: cấp độ heading (1=top, 2=sub...) — hỗ trợ parent→child expansion trong retrieval
                    .put("heading_level", safeInt(chunk.getHeadingLevel()));

            // child_section_ids: chỉ có ở parent_section_summary — dùng để auto-expand retrieval
            if (chunk.getChildSectionIds() != null && !chunk.getChildSectionIds().isBlank()) {
                metadata.put("child_section_ids", chunk.getChildSectionIds());
            }

            if (chunk.getTableId() != null && !chunk.getTableId().isBlank()) {
                metadata.put("table_id", chunk.getTableId());
            }

            if ("normalized_table_row".equalsIgnoreCase(safeString(chunk.getChunkType()))) {
                putIfPresent(metadata, "table_name", chunk.getTableName());
                if (chunk.getRowIndex() != null) {
                    metadata.put("row_index", chunk.getRowIndex());
                }
                putIfPresent(metadata, "cells_json", chunk.getCellsJson());
                putIfPresent(metadata, "group_context", chunk.getGroupContext());
            }

            if (chunk.getPrevChunk() != null && chunk.getPrevChunk().getId() != null) {
                metadata.put("prev_chunk_id", chunk.getPrevChunk().getId().toString());
            }

            if (chunk.getNextChunk() != null && chunk.getNextChunk().getId() != null) {
                metadata.put("next_chunk_id", chunk.getNextChunk().getId().toString());
            }

            // Quan trọng: embed kèm heading/section để query theo "tiêu đề mục" (vd: "Nền tảng & kiến trúc")
            // vẫn match tốt ngay cả khi content không lặp lại tiêu đề.
            String embedText = buildEmbeddingText(chunk);
            segments.add(TextSegment.from(embedText, metadata));
        }

        List<Embedding> embeddings = embeddingModel.embedAll(segments).content();

        upsertToQdrant(embeddings, segments, documentId);

        log.info("Đã lưu {} vectors vào Qdrant cho document={}", embeddings.size(), documentId);
    }

    /**
     * Upsert embeddings + metadata to Qdrant via REST (HTTP) API.
     *
     * <p>Uses Spring {@link RestClient} → Jackson JSON serialization, which preserves UTF-8/Unicode
     * exactly. This replaces the previous LangChain4j gRPC write path that corrupted Vietnamese
     * Unicode characters in payload strings (mojibake bug, root cause: gRPC protobuf bytes
     * conversion did not preserve multi-byte UTF-8 characters correctly).</p>
     */
    private void upsertToQdrant(List<Embedding> embeddings, List<TextSegment> segments, UUID documentId) {
        if (embeddings.isEmpty()) {
            return;
        }

        List<Map<String, Object>> points = new ArrayList<>(embeddings.size());
        for (int i = 0; i < embeddings.size(); i++) {
            Embedding emb = embeddings.get(i);
            TextSegment seg = segments.get(i);

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("text_segment", seg.text());
            payload.putAll(seg.metadata().toMap());

            Map<String, Object> point = new LinkedHashMap<>();
            point.put("id", UUID.randomUUID().toString());
            point.put("vector", emb.vectorAsList());
            point.put("payload", payload);
            points.add(point);
        }

        String upsertUrl = qdrantBaseUrl() + "/collections/" + qdrantCollectionName + "/points";
        int total = points.size();
        int batches = 0;
        for (int start = 0; start < total; start += QDRANT_UPSERT_BATCH_SIZE) {
            int end = Math.min(start + QDRANT_UPSERT_BATCH_SIZE, total);
            List<Map<String, Object>> batch = points.subList(start, end);
            restClient.put()
                    .uri(upsertUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("points", batch))
                    .retrieve()
                    .toBodilessEntity();
            batches++;
        }
        log.info("[EmbeddingUpsert] document={} points={} batches={}", documentId, total, batches);
    }

    private String buildEmbeddingText(KLTN.RAG_CHATBOT_BE.domain.document.DocumentChunk chunk) {
        if (chunk == null) {
            return "";
        }

        String heading = safeString(chunk.getHeadingPathText()).trim();
        String sectionTitle = safeString(chunk.getSectionTitle()).trim();
        String type = safeString(chunk.getChunkType()).trim();
        String content = safeString(chunk.getContent()).trim();

        StringBuilder sb = new StringBuilder();
        if (!heading.isBlank()) {
            sb.append(heading).append("\n");
        } else if (!sectionTitle.isBlank()) {
            sb.append(sectionTitle).append("\n");
        }
        if (!type.isBlank()) {
            sb.append("Type: ").append(type).append("\n");
        }
        sb.append(content);
        return sb.toString().trim();
    }

    public List<TextSegment> search(String query, int topK, UUID widgetId) {
        if (widgetId == null) {
            return List.of();
        }

        List<Float> queryVector = getCachedOrEmbedQuery(query);
        if (queryVector.isEmpty()) {
            log.warn("[EmbeddingSearch] Query embedding is empty for query='{}'", query);
            return List.of();
        }

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("vector", queryVector);
        request.put("limit", topK);
        request.put("with_payload", true);
        request.put("with_vector", false);
        request.put("filter", Map.of(
                "must", List.of(Map.of(
                        "key", "widgetId",
                        "match", Map.of("value", widgetId.toString())
                ))
        ));

        long qdrantStart = RagLatencyTrace.now();
        Map<?, ?> response;
        try {
            response = restClient.post()
                    .uri(qdrantBaseUrl() + "/collections/" + qdrantCollectionName + "/points/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(Map.class);
        } finally {
            RagLatencyTrace trace = RagLatencyTrace.current();
            if (trace != null) {
                trace.addVectorMs(RagLatencyTrace.elapsedMs(qdrantStart));
            }
        }

        List<?> points = response == null ? List.of() : asList(response.get("result"));
        log.info("[EmbeddingSearch] query='{}', widgetId={}, matches={}", query, widgetId, points.size());

        return points.stream()
                .map(this::toTextSegment)
                .filter(segment -> segment != null && segment.text() != null && !segment.text().isBlank())
                .toList();
    }

    private List<Float> getCachedOrEmbedQuery(String query) {
        String cacheKey = queryCacheKey(query);
        long now = System.currentTimeMillis();
        CachedEmbedding cached = queryEmbeddingCache.get(cacheKey);
        if (cached != null && now - cached.createdAtMs() <= QUERY_EMBEDDING_CACHE_TTL_MS) {
            log.info("[RAG][embedding-cache] hit=true keyHash={}", cacheKey.hashCode());
            return cached.vector();
        }
        if (cached != null) {
            queryEmbeddingCache.remove(cacheKey);
        }

        log.info("[RAG][embedding-cache] hit=false keyHash={}", cacheKey.hashCode());
        RagTokenAudit.incrementEmbeddingCalls();
        long embedStart = RagLatencyTrace.now();
        Embedding queryEmbedding;
        try {
            queryEmbedding = embeddingModel.embedAll(List.of(TextSegment.from(query))).content().get(0);
        } finally {
            RagLatencyTrace trace = RagLatencyTrace.current();
            if (trace != null) {
                trace.addQueryEmbedMs(RagLatencyTrace.elapsedMs(embedStart));
            }
        }
        if (queryEmbedding == null || queryEmbedding.dimension() == 0) {
            return List.of();
        }
        List<Float> vector = List.copyOf(queryEmbedding.vectorAsList());
        if (!vector.isEmpty()) {
            queryEmbeddingCache.put(cacheKey, new CachedEmbedding(vector, now));
        }
        return vector;
    }

    private String queryCacheKey(String query) {
        return queryCacheKey(query, embeddingModel.getClass().getName(), qdrantCollectionName);
    }

    public static String queryCacheKey(String query, String embeddingProvider, String collectionName) {
        String normalized = query == null ? "" : query.replaceAll("\\s+", " ")
                .trim()
                .toLowerCase(Locale.ROOT);
        return (embeddingProvider == null ? "" : embeddingProvider)
                + "|" + (collectionName == null ? "" : collectionName)
                + "|" + normalized;
    }

    record CachedEmbedding(List<Float> vector, long createdAtMs) {}

    private String qdrantBaseUrl() {
        return "http://" + qdrantHost + ":" + qdrantHttpPort;
    }

    @SuppressWarnings("unchecked")
    private List<?> asList(Object value) {
        return value instanceof List<?> list ? list : List.of();
    }

    @SuppressWarnings("unchecked")
    private TextSegment toTextSegment(Object point) {
        if (!(point instanceof Map<?, ?> pointMap)) {
            return null;
        }

        Object payloadValue = pointMap.get("payload");
        if (!(payloadValue instanceof Map<?, ?> payload)) {
            return null;
        }

        String text = stringValue(payload.get("text_segment"));
        if (text.isBlank()) {
            return null;
        }

        Metadata metadata = new Metadata();
        for (Map.Entry<?, ?> entry : payload.entrySet()) {
            String key = stringValue(entry.getKey());
            if (key.isBlank() || "text_segment".equals(key)) {
                continue;
            }

            Object value = entry.getValue();
            if (value instanceof Number number) {
                metadata.put(key, number.intValue());
            } else {
                metadata.put(key, stringValue(value));
            }
        }

        return TextSegment.from(text, metadata);
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String safeString(String value) {
        return value == null ? "" : value;
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private static void putIfPresent(Metadata metadata, String key, String value) {
        if (value != null && !value.isBlank()) {
            metadata.put(key, value);
        }
    }
}
