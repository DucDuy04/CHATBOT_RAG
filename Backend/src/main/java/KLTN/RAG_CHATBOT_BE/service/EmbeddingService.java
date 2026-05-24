package KLTN.RAG_CHATBOT_BE.service;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.qdrant.QdrantEmbeddingStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingService {

    private final EmbeddingModel embeddingModel;
    private final QdrantEmbeddingStore qdrantEmbeddingStore;
    private final RestClient restClient = RestClient.create();

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

        qdrantEmbeddingStore.addAll(embeddings, segments);

        log.info("Đã lưu {} vectors vào Qdrant cho document={}", embeddings.size(), documentId);
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

        RagTokenAudit.incrementEmbeddingCalls();
        Embedding queryEmbedding = embeddingModel.embedAll(List.of(TextSegment.from(query))).content().get(0);
        if (queryEmbedding.dimension() == 0) {
            log.warn("[EmbeddingSearch] Query embedding is empty for query='{}'", query);
            return List.of();
        }

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("vector", queryEmbedding.vectorAsList());
        request.put("limit", topK);
        request.put("with_payload", true);
        request.put("with_vector", false);
        request.put("filter", Map.of(
                "must", List.of(Map.of(
                        "key", "widgetId",
                        "match", Map.of("value", widgetId.toString())
                ))
        ));

        Map<?, ?> response = restClient.post()
                .uri(qdrantBaseUrl() + "/collections/" + qdrantCollectionName + "/points/search")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(Map.class);

        List<?> points = response == null ? List.of() : asList(response.get("result"));
        log.info("[EmbeddingSearch] query='{}', widgetId={}, matches={}", query, widgetId, points.size());

        return points.stream()
                .map(this::toTextSegment)
                .filter(segment -> segment != null && segment.text() != null && !segment.text().isBlank())
                .toList();
    }

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
