package KLTN.RAG_CHATBOT_BE.service;

import KLTN.RAG_CHATBOT_BE.domain.document.DocumentChunk;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.*;

/**
 * Rerank service: sau khi Qdrant trả về top-K candidates,
 * dùng Cross-Encoder (Cohere Rerank API) để chấm điểm lại từng cặp (query, document)
 * và giữ lại top-N có điểm cao nhất trước khi đưa vào LLM.
 *
 * <p>Nếu {@code cohere.rerank.enabled=false} hoặc không có API key,
 * service sẽ trả về danh sách gốc (graceful fallback).
 *
 * <p>Model đề xuất: {@code rerank-multilingual-v3.0} — hỗ trợ tiếng Việt tốt.
 *
 * <p>Khi {@code maxScore < LOW_CONFIDENCE_THRESHOLD}, query có thể có typo hoặc không
 * khớp với bất kỳ nội dung nào trong tài liệu — caller nên log cảnh báo.
 */
@Slf4j
@Service
public class RerankService {

    private static final String COHERE_RERANK_URL = "https://api.cohere.com/v2/rerank";

    /** Số chunk tối đa gửi lên Cohere (tránh timeout & cost). */
    private static final int MAX_DOCS_TO_RERANK = 50;

    /**
     * Ngưỡng để coi kết quả rerank là "low confidence".
     * Nếu maxScore < threshold → query không khớp tốt với bất kỳ chunk nào.
     * Cohere rerank score: 0.0–1.0 (cross-encoder probability).
     * Threshold 0.1 = dưới 10% confidence → rất có thể query bị typo hoặc OOD.
     */
    public static final double LOW_CONFIDENCE_THRESHOLD = 0.10;

    private final RestClient restClient = RestClient.create();

    @Value("${cohere.api-key:}")
    private String cohereApiKey;

    @Value("${cohere.rerank.enabled:false}")
    private boolean rerankEnabled;

    @Value("${cohere.rerank.model:rerank-multilingual-v3.0}")
    private String rerankModel;

    /**
     * Kết quả trả về từ rerank, bao gồm danh sách chunks đã lọc và thống kê score.
     *
     * @param chunks   Top-N chunks sắp xếp theo relevance score (cao → thấp).
     * @param maxScore Điểm cao nhất trong batch — dùng để phát hiện low-confidence query.
     * @param minScore Điểm thấp nhất trong batch.
     */
    public record RerankResult(List<DocumentChunk> chunks, double maxScore, double minScore) {
        /** Tạo fallback result khi rerank không thể chạy (disabled, API error...) */
        public static RerankResult fallback(List<DocumentChunk> original) {
            return new RerankResult(original, 1.0, 0.0);
        }
    }

    /**
     * Chunk với điểm relevance từ Cohere (hoặc fallback).
     */
    public record ScoredChunk(DocumentChunk chunk, double score) {}

    /**
     * Rerank danh sách chunks theo độ liên quan với query.
     *
     * @param query  Câu hỏi gốc của người dùng.
     * @param chunks Danh sách chunks đã được collect từ vector search + expansion.
     * @param topN   Số lượng chunk giữ lại sau khi rerank — nên đặt bằng FINAL_LIMIT + buffer nhỏ,
     *               KHÔNG phải gấp đôi, để reranker thực sự lọc bớt.
     * @return       RerankResult chứa top-N chunks (cao → thấp) + score statistics.
     *               Caller (dedupeSortBudget) sẽ sắp lại theo thứ tự tài liệu sau đó.
     */
    public RerankResult rerank(String query, List<DocumentChunk> chunks, int topN) {
        if (!rerankEnabled || cohereApiKey == null || cohereApiKey.isBlank()) {
            log.debug("[Rerank] Bỏ qua — disabled hoặc không có API key.");
            return RerankResult.fallback(chunks);
        }

        if (chunks == null || chunks.isEmpty()) return new RerankResult(List.of(), 0.0, 0.0);

        // Dedup theo chunkId trước khi gửi lên API
        Map<UUID, DocumentChunk> uniqueMap = new LinkedHashMap<>();
        for (DocumentChunk c : chunks) {
            if (c.getId() != null) uniqueMap.putIfAbsent(c.getId(), c);
        }
        List<DocumentChunk> uniqueList = new ArrayList<>(uniqueMap.values());

        // Giới hạn số documents gửi lên Cohere
        List<DocumentChunk> toRerank = uniqueList.size() > MAX_DOCS_TO_RERANK
                ? uniqueList.subList(0, MAX_DOCS_TO_RERANK)
                : uniqueList;

        // Chuẩn bị text cho từng document: sectionTitle + content
        List<String> documents = toRerank.stream()
                .map(c -> {
                    String title = c.getSectionTitle() != null
                            ? c.getSectionTitle().trim() + ". " : "";
                    String content = c.getContent() != null ? c.getContent() : "";
                    return title + content;
                })
                .toList();

        int effectiveTopN = Math.min(topN, toRerank.size());

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", rerankModel);
        requestBody.put("query", query);
        requestBody.put("documents", documents);
        requestBody.put("top_n", effectiveTopN);
        requestBody.put("return_documents", false);

        try {
            Map<?, ?> response = restClient.post()
                    .uri(COHERE_RERANK_URL)
                    .header("Authorization", "Bearer " + cohereApiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(Map.class);

            if (response == null || !response.containsKey("results")) {
                log.warn("[Rerank] Response rỗng hoặc thiếu 'results', fallback về list gốc.");
                return RerankResult.fallback(chunks);
            }

            List<?> results = (List<?>) response.get("results");
            List<DocumentChunk> reranked = new ArrayList<>();
            double maxScore = 0.0;
            double minScore = Double.MAX_VALUE;

            for (Object item : results) {
                Map<?, ?> resultMap = (Map<?, ?>) item;
                int index = ((Number) resultMap.get("index")).intValue();
                double score = ((Number) resultMap.get("relevance_score")).doubleValue();
                DocumentChunk chunk = toRerank.get(index);
                reranked.add(chunk);

                maxScore = Math.max(maxScore, score);
                minScore = Math.min(minScore, score);

                log.debug("[Rerank] index={} score={} sectionId={} chunkType={}",
                        index, String.format("%.4f", score),
                        chunk.getSectionId(), chunk.getChunkType());
            }

            if (minScore == Double.MAX_VALUE) minScore = 0.0;

            RagTokenAudit.incrementRerankCalls();
            log.info("[Rerank] {} chunks → top {} (model={}) | maxScore={} minScore={}",
                    toRerank.size(), reranked.size(), rerankModel,
                    String.format("%.4f", maxScore), String.format("%.4f", minScore));
            return new RerankResult(reranked, maxScore, minScore);

        } catch (Exception e) {
            log.error("[Rerank] Cohere API lỗi: {} — fallback về list gốc.", e.getMessage());
            return RerankResult.fallback(chunks);
        }
    }

    public boolean isEnabled() {
        return rerankEnabled && cohereApiKey != null && !cohereApiKey.isBlank();
    }

    /**
     * Chấm điểm toàn bộ candidate pool (tối đa {@link #MAX_DOCS_TO_RERANK}).
     * Trả về danh sách có score, sắp xếp cao → thấp.
     */
    public List<ScoredChunk> scoreCandidates(String query, List<DocumentChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return List.of();
        }

        Map<UUID, DocumentChunk> uniqueMap = new LinkedHashMap<>();
        for (DocumentChunk c : chunks) {
            if (c.getId() != null) {
                uniqueMap.putIfAbsent(c.getId(), c);
            }
        }
        List<DocumentChunk> uniqueList = new ArrayList<>(uniqueMap.values());
        if (uniqueList.isEmpty()) {
            return List.of();
        }

        if (!isEnabled()) {
            return List.of();
        }

        List<DocumentChunk> toScore = uniqueList.size() > MAX_DOCS_TO_RERANK
                ? uniqueList.subList(0, MAX_DOCS_TO_RERANK)
                : uniqueList;

        List<String> documents = toScore.stream()
                .map(c -> {
                    String title = c.getSectionTitle() != null
                            ? c.getSectionTitle().trim() + ". " : "";
                    String content = c.getContent() != null ? c.getContent() : "";
                    return title + content;
                })
                .toList();

        int topN = toScore.size();
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", rerankModel);
        requestBody.put("query", query);
        requestBody.put("documents", documents);
        requestBody.put("top_n", topN);
        requestBody.put("return_documents", false);

        try {
            Map<?, ?> response = restClient.post()
                    .uri(COHERE_RERANK_URL)
                    .header("Authorization", "Bearer " + cohereApiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(Map.class);

            if (response == null || !response.containsKey("results")) {
                log.warn("[Rerank] scoreCandidates: response rỗng, không có score.");
                return List.of();
            }

            List<?> results = (List<?>) response.get("results");
            List<ScoredChunk> scored = new ArrayList<>();
            for (Object item : results) {
                Map<?, ?> resultMap = (Map<?, ?>) item;
                int index = ((Number) resultMap.get("index")).intValue();
                double score = ((Number) resultMap.get("relevance_score")).doubleValue();
                scored.add(new ScoredChunk(toScore.get(index), score));
            }
            scored.sort(Comparator.comparingDouble(ScoredChunk::score).reversed());
            RagTokenAudit.incrementRerankCalls();
            log.info("[Rerank] scoreCandidates: {} docs scored (model={})", scored.size(), rerankModel);
            return scored;
        } catch (Exception e) {
            log.error("[Rerank] scoreCandidates API lỗi: {}", e.getMessage());
            return List.of();
        }
    }
}
