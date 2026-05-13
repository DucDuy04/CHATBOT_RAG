package KLTN.RAG_CHATBOT_BE.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Purge Qdrant points by payload filter (document + tenant). Uses HTTP API consistent with {@link EmbeddingService#search}.
 */
@Slf4j
@Service
public class QdrantPurgeService {

    private static final int LOG_BODY_MAX = 400;

    private final RestClient restClient = RestClient.create();

    @Value("${qdrant.host}")
    private String qdrantHost;

    @Value("${qdrant.http-port}")
    private int qdrantHttpPort;

    @Value("${qdrant.collection-name}")
    private String collectionName;

    private String baseUrl() {
        return "http://" + qdrantHost + ":" + qdrantHttpPort;
    }

    /**
     * Delete all points whose payload matches both {@code document_id} and {@code widgetId}
     * (same keys as {@link EmbeddingService#embedAndStore}).
     */
    public void purgeDocumentVectors(UUID documentId, UUID widgetId) {
        if (documentId == null || widgetId == null) {
            throw new IllegalStateException("Failed to purge document vectors from Qdrant: missing documentId or widgetId");
        }

        Map<String, Object> filter = Map.of(
                "must", List.of(
                        Map.of("key", "document_id", "match", Map.of("value", documentId.toString())),
                        Map.of("key", "widgetId", "match", Map.of("value", widgetId.toString()))
                )
        );

        Integer matched = countPoints(filter);
        if (matched != null) {
            log.info("[QdrantPurge] documentId={} widgetId={} points_matched_by_count={}", documentId, widgetId, matched);
        }

        String deleteUrl = baseUrl() + "/collections/" + collectionName + "/points/delete";
        try {
            ResponseEntity<String> res = restClient.post()
                    .uri(deleteUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("filter", filter))
                    .retrieve()
                    .toEntity(String.class);

            if (!res.getStatusCode().is2xxSuccessful()) {
                log.warn("[QdrantPurge] delete HTTP {} body_snip={}", res.getStatusCode(), truncate(res.getBody()));
                throw new IllegalStateException("Failed to purge document vectors from Qdrant");
            }
            if (matched != null && matched == 0) {
                log.info("[QdrantPurge] delete OK; no vectors matched (document may have no embeddings); documentId={}",
                        documentId);
            } else {
                log.info("[QdrantPurge] delete OK for documentId={} widgetId={}", documentId, widgetId);
            }
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to purge document vectors from Qdrant: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Integer countPoints(Map<String, Object> filter) {
        String countUrl = baseUrl() + "/collections/" + collectionName + "/points/count";
        try {
            ResponseEntity<Map> res = restClient.post()
                    .uri(countUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("filter", filter, "exact", true))
                    .retrieve()
                    .toEntity(Map.class);
            if (!res.getStatusCode().is2xxSuccessful() || res.getBody() == null) {
                return null;
            }
            Object result = res.getBody().get("result");
            if (result instanceof Map<?, ?> r && r.get("count") instanceof Number n) {
                return n.intValue();
            }
        } catch (Exception e) {
            log.debug("[QdrantPurge] count skipped: {}", e.getMessage());
        }
        return null;
    }

    private static String truncate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() <= LOG_BODY_MAX ? s : s.substring(0, LOG_BODY_MAX) + "…";
    }
}
