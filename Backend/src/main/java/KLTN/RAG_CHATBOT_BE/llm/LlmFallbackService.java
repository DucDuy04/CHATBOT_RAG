package KLTN.RAG_CHATBOT_BE.llm;

import KLTN.RAG_CHATBOT_BE.audit.metrics.RagTokenAudit;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.model.output.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

/**
 * Quản lý việc gọi LLM với cơ chế fallback tự động khi model chính bị rate limit.
 *
 * Phân biệt 2 loại rate limit của Groq:
 *  - TPD (tokens per day):     hết quota ngày → skip ngay sang model khác
 *  - TPM (tokens per minute):  quá nhanh trong 1 phút → đợi ~15s rồi retry cùng model
 *  - model_decommissioned:     model bị xóa → skip ngay
 */
@Slf4j
@Service
public class LlmFallbackService {

    @Value("${groq.api-key}")
    private String groqApiKey;

    @Value("${groq.chat-model}")
    private String primaryModel;

    @Value("${groq.base-url}")
    private String groqBaseUrl;

    // Dùng String thay vì List<String> để tránh lỗi inject YAML list với @Value
    // Format: "llama-3.1-8b-instant,gemma2-9b-it"
    @Value("${groq.fallback-models:}")
    private String fallbackModelsConfig;

    // ==========================================
    // NON-STREAMING: trả về answer String
    // ==========================================
    public String generateWithFallback(List<ChatMessage> messages) {
        return generateWithFallback(messages, LlmGenerationOptions.defaults());
    }

    public String generateWithFallback(List<ChatMessage> messages, LlmGenerationOptions options) {
        LlmGenerationOptions effective = options != null ? options : LlmGenerationOptions.defaults();
        List<String> modelsToTry = buildModelList();

        for (int i = 0; i < modelsToTry.size(); i++) {
            String modelName = modelsToTry.get(i);
            try {
                RagTokenAudit.incrementLlmCallIndex();
                log.info("[LLM] Thử model: {} (attempt {}/{})", modelName, i + 1, modelsToTry.size());
                OpenAiChatModel model = buildChatModel(modelName, effective);
                Response<AiMessage> response = model.generate(messages);
                RagTokenAudit.recordActualFromResponse(response);
                RagTokenAudit.setResolvedModel(modelName);
                String answer = response.content().text();
                if (i > 0) {
                    log.info("[LLM] Fallback thành công với model: {}", modelName);
                }
                return answer;
            } catch (Exception e) {
                RagTokenAudit.recordLlmFailure(e, modelName, i + 1);
                if (isModelDecommissioned(e)) {
                    log.warn("[LLM] Model '{}' đã bị decommission. Bỏ qua.", modelName);
                } else if (isTpmException(e)) {
                    // "Request too large for model ... (TPM)" không phải TPM tạm thời để đợi,
                    // mà là prompt quá lớn so với TPM limit của model đó → retry vô ích, cần đổi model.
                    if (isTpmRequestTooLarge(e)) {
                        log.warn("[LLM] Model '{}' bị TPM do request quá lớn. Chuyển model tiếp theo.", modelName);
                    } else {
                        int waitSeconds = parseRetryAfterSeconds(e.getMessage(), 15);
                        log.warn("[LLM] Model '{}' bị TPM. Đợi {}s...", modelName, waitSeconds);
                        sleepQuietly(waitSeconds);
                        i--; // retry cùng model
                    }
                } else if (isRateLimitException(e)) {
                    log.warn("[LLM] Model '{}' bị TPD (hết quota ngày). Chuyển model tiếp theo.", modelName);
                } else {
                    log.error("[LLM] Model '{}' lỗi: {}", modelName, e.getMessage());
                    throw e;
                }
            }
        }

        log.error("[LLM] Tất cả {} model đều bị rate limit hoặc lỗi.", modelsToTry.size());
        return "Dịch vụ AI hiện đang quá tải. Vui lòng thử lại sau ít phút.";
    }

    // ==========================================
    // STREAMING: thử từng model, nếu rate limit dùng fallback non-streaming
    // rồi phát token giả lập để SSE vẫn hoạt động
    // ==========================================
    public void generateStreamingWithFallback(
            List<ChatMessage> messages,
            StreamingResponseHandler<AiMessage> handler,
            Runnable onRateLimitFallback
    ) {
        String modelName = primaryModel;
        try {
            log.info("[LLM-Stream] Thử streaming với model: {}", modelName);
            OpenAiStreamingChatModel streamModel = buildStreamingModel(modelName, LlmGenerationOptions.defaults());
            streamModel.generate(messages, handler);

        } catch (Exception e) {
            if (isRateLimitException(e)) {
                log.warn("[LLM-Stream] Model '{}' bị rate limit trước khi stream. Chuyển sang fallback non-streaming.", modelName);
                onRateLimitFallback.run();
            } else {
                handler.onError(e);
            }
        }
    }

    /**
     * Gọi non-streaming với fallback, dành cho khi streaming bị rate limit.
     * Kết quả trả về để caller tự phát token qua SSE.
     */
    public String generateFallbackAnswer(List<ChatMessage> messages) {
        return generateFallbackAnswer(messages, LlmGenerationOptions.defaults());
    }

    public String generateFallbackAnswer(List<ChatMessage> messages, LlmGenerationOptions options) {
        LlmGenerationOptions effective = options != null ? options : LlmGenerationOptions.defaults();
        // Chỉ dùng fallback models — KHÔNG thử lại primary (đã biết rate limit)
        List<String> onlyFallbacks = parseFallbackModels();

        if (onlyFallbacks.isEmpty()) {
            log.warn("[LLM-Fallback] Không có fallback model nào được cấu hình (groq.fallback-models trống).");
            return "Dịch vụ AI hiện đang quá tải. Vui lòng thử lại sau ít phút (model chính đã hết quota ngày).";
        }

        log.info("[LLM-Fallback] Bắt đầu thử {} fallback models: {}", onlyFallbacks.size(), onlyFallbacks);

        for (int i = 0; i < onlyFallbacks.size(); i++) {
            String modelName = onlyFallbacks.get(i);
            try {
                RagTokenAudit.incrementLlmCallIndex();
                log.info("[LLM-Fallback] Thử fallback model: {} ({}/{})", modelName, i + 1, onlyFallbacks.size());
                OpenAiChatModel model = buildChatModel(modelName, effective);
                Response<AiMessage> response = model.generate(messages);
                RagTokenAudit.recordActualFromResponse(response);
                RagTokenAudit.setResolvedModel(modelName);
                String answer = response.content().text();
                log.info("[LLM-Fallback] Thành công với model: {}", modelName);
                return answer;
            } catch (Exception e) {
                RagTokenAudit.recordLlmFailure(e, modelName, i + 1);
                if (isModelDecommissioned(e)) {
                    // Model bị Groq xóa → skip ngay, không retry
                    log.warn("[LLM-Fallback] Model '{}' đã bị decommission. Bỏ qua.", modelName);
                } else if (isTpmException(e)) {
                    // TPM (tokens per minute) → đợi rồi retry CÙNG model (không skip)
                    // Nhưng nếu là "Request too large ... (TPM)" thì retry vô ích → skip model tiếp theo.
                    if (isTpmRequestTooLarge(e)) {
                        log.warn("[LLM-Fallback] Model '{}' bị TPM do request quá lớn. Chuyển sang model tiếp theo.", modelName);
                    } else {
                        int waitSeconds = parseRetryAfterSeconds(e.getMessage(), 15);
                        log.warn("[LLM-Fallback] Model '{}' bị TPM rate limit. Đợi {}s rồi thử lại...",
                                modelName, waitSeconds);
                        sleepQuietly(waitSeconds);
                        i--; // Giữ nguyên index để retry cùng model
                    }
                } else if (isRateLimitException(e)) {
                    // TPD (tokens per day) → hết quota ngày → skip sang model khác
                    log.warn("[LLM-Fallback] Model '{}' bị TPD rate limit (hết quota ngày). Chuyển sang model tiếp theo.", modelName);
                } else {
                    log.error("[LLM-Fallback] Model '{}' lỗi không xác định: {}", modelName, e.getMessage());
                    // Lỗi khác cũng thử model tiếp theo thay vì crash toàn bộ
                }
            }
        }

        log.error("[LLM-Fallback] Tất cả {} fallback models đều thất bại.", onlyFallbacks.size());
        return "Dịch vụ AI hiện đang quá tải. Vui lòng thử lại sau ít phút.";
    }

    // ==========================================
    // HELPERS
    // ==========================================

    /**
     * Xây danh sách model theo thứ tự: primary → fallback[0] → fallback[1] → ...
     */
    private List<String> buildModelList() {
        List<String> fallbacks = parseFallbackModels();
        if (fallbacks.isEmpty()) {
            return List.of(primaryModel);
        }
        java.util.List<String> all = new java.util.ArrayList<>();
        all.add(primaryModel);
        all.addAll(fallbacks);
        return all;
    }

    /**
     * Parse danh sách fallback models từ config string "model1,model2,model3".
     * Trả về list rỗng nếu không được cấu hình.
     */
    private List<String> parseFallbackModels() {
        if (fallbackModelsConfig == null || fallbackModelsConfig.isBlank()) {
            return List.of();
        }
        return Arrays.stream(fallbackModelsConfig.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();
    }

    /**
     * Phát hiện bất kỳ dạng rate limit nào (TPM hoặc TPD).
     */
    public boolean isRateLimitException(Throwable e) {
        return containsInChain(e, "rate_limit_exceeded") || containsInChain(e, "Rate limit");
    }

    /**
     * TPM (tokens per minute): "tokens per minute" trong message.
     * Loại này reset sau vài giây — nên đợi rồi retry cùng model.
     */
    private boolean isTpmException(Throwable e) {
        return containsInChain(e, "tokens per minute") || containsInChain(e, "TPM");
    }

    /**
     * Groq đôi khi trả về rate_limit_exceeded với message dạng:
     * "Request too large for model ... on tokens per minute (TPM): Limit X, Requested Y"
     * Trường hợp này đợi rồi retry vẫn fail → cần giảm prompt hoặc đổi model.
     */
    private boolean isTpmRequestTooLarge(Throwable e) {
        return containsInChain(e, "Request too large for model")
                && (containsInChain(e, "tokens per minute") || containsInChain(e, "(TPM)"));
    }

    /**
     * Model đã bị Groq decommission — không retry, skip ngay.
     */
    private boolean isModelDecommissioned(Throwable e) {
        return containsInChain(e, "model_decommissioned") || containsInChain(e, "decommissioned");
    }

    private boolean containsInChain(Throwable e, String keyword) {
        if (e == null) return false;
        String msg = e.getMessage();
        if (msg != null && msg.contains(keyword)) return true;
        return containsInChain(e.getCause(), keyword);
    }

    /**
     * Parse thời gian cần đợi từ message Groq.
     * Ví dụ: "Please try again in 9.43s" → 10
     */
    private int parseRetryAfterSeconds(String message, int defaultSeconds) {
        if (message == null) return defaultSeconds;
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("try again in ([\\d.]+)s")
                .matcher(message);
        if (m.find()) {
            try {
                return (int) Math.ceil(Double.parseDouble(m.group(1))) + 2; // +2s buffer
            } catch (NumberFormatException ignored) {
            }
        }
        return defaultSeconds;
    }

    private void sleepQuietly(int seconds) {
        try {
            Thread.sleep(seconds * 1000L);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    public OpenAiChatModel buildChatModel(String modelName, LlmGenerationOptions options) {
        LlmGenerationOptions effective = options != null ? options : LlmGenerationOptions.defaults();
        return OpenAiChatModel.builder()
                .apiKey(groqApiKey)
                .baseUrl(groqBaseUrl)
                .modelName(modelName)
                .temperature(effective.temperature())
                .maxTokens(effective.maxTokens())
                .build();
    }

    public OpenAiStreamingChatModel buildStreamingModel(String modelName, LlmGenerationOptions options) {
        LlmGenerationOptions effective = options != null ? options : LlmGenerationOptions.defaults();
        return OpenAiStreamingChatModel.builder()
                .apiKey(groqApiKey)
                .baseUrl(groqBaseUrl)
                .modelName(modelName)
                .temperature(effective.temperature())
                .maxTokens(effective.maxTokens())
                .build();
    }
}
