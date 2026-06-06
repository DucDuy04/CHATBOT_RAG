package KLTN.RAG_CHATBOT_BE.rag.analysis;

import KLTN.RAG_CHATBOT_BE.llm.LlmFallbackService;
import KLTN.RAG_CHATBOT_BE.llm.LlmGenerationOptions;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * LLM-based query intent classifier with timeout and safe fallback.
 *
 * <p>Design rules:
 * <ul>
 *   <li>Prompt is generic — no domain examples, no project-specific terms.</li>
 *   <li>LLM response must be JSON only; non-JSON or unknown enum → fallback.</li>
 *   <li>Timeout (default 1200 ms) prevents blocking the request thread.</li>
 *   <li>Any failure returns {@link QueryAnalysisResult#ofFallback} — never throws.</li>
 *   <li>Classifier options: temperature 0.0 (deterministic), max-tokens 150.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LlmQueryClassifier {

    /**
     * Generic system prompt — no domain vocabulary.
     * Definitions must match {@link QueryAnalyzerService.QueryType} exactly.
     */
    private static final String SYSTEM_PROMPT = """
            You are a query intent classifier for a document-based RAG system.
            Classify the user query into exactly ONE of these types:

            NORMAL_FACT       - Simple factual question; no full list or table strategy needed.
            LIST_ALL          - Asks for all items, a full enumeration, or a complete list.
            TABLE_LOOKUP      - Asks for value(s) from a table row, schedule, structured record, or column attribute.
            SECTION_SUMMARY   - Asks to summarize or explain a section, topic, or concept.
            COUNT_QUERY       - Asks for a count or number of items, rows, or records.
            CROSS_PAGE_SECTION - Asks about a long section spanning multiple pages.

            Reply ONLY with valid JSON — no other text, no markdown:
            {"queryType":"TYPE","confidence":0.85,"evidence":["short reason"]}
            """;

    private static final ObjectMapper JSON = new ObjectMapper();

    private final LlmFallbackService llmService;

    @Value("${groq.chat-model}")
    private String primaryModel;

    @Value("${rag.analysis.llm-classifier.timeout-ms:1200}")
    private int timeoutMs;

    @Value("${rag.analysis.llm-classifier.max-input-chars:1000}")
    private int maxInputChars;

    @Value("${rag.analysis.llm-classifier.min-accepted-confidence:0.65}")
    private double minAcceptedConfidence;

    /** LLM options: deterministic (temp=0), short response. */
    private static final LlmGenerationOptions CLASSIFIER_OPTIONS = new LlmGenerationOptions(0.0, 150);

    /**
     * Classify {@code question} using the LLM.
     *
     * <p>Always returns a non-null result. On timeout, invalid response, or any exception,
     * returns {@link QueryAnalysisResult#ofFallback}.
     *
     * @param question original user question
     * @return analysis result; source is LLM_CLASSIFIER on success, FALLBACK_DEFAULT on failure
     */
    public QueryAnalysisResult classify(String question) {
        if (question == null || question.isBlank()) {
            return QueryAnalysisResult.ofFallback(
                    QueryAnalyzerService.QueryType.NORMAL_FACT, "empty question");
        }

        String truncated = question.length() > maxInputChars
                ? question.substring(0, maxInputChars)
                : question;

        List<ChatMessage> messages = List.of(
                SystemMessage.from(SYSTEM_PROMPT),
                UserMessage.from(truncated));

        CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
            OpenAiChatModel model = llmService.buildChatModel(primaryModel, CLASSIFIER_OPTIONS);
            return model.generate(messages).content().text();
        });

        try {
            String raw = future.get(timeoutMs, TimeUnit.MILLISECONDS);
            return parseResponse(raw);
        } catch (TimeoutException e) {
            future.cancel(true);
            log.warn("[QueryAnalysis][LLM] classifier timeout after {}ms — fallback to local", timeoutMs);
            return QueryAnalysisResult.ofFallback(
                    QueryAnalyzerService.QueryType.NORMAL_FACT, "timeout");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[QueryAnalysis][LLM] classifier interrupted — fallback to local");
            return QueryAnalysisResult.ofFallback(
                    QueryAnalyzerService.QueryType.NORMAL_FACT, "interrupted");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            String msg = cause != null ? cause.getClass().getSimpleName() : "ExecutionException";
            log.warn("[QueryAnalysis][LLM] classifier error={} — fallback to local", msg);
            return QueryAnalysisResult.ofFallback(
                    QueryAnalyzerService.QueryType.NORMAL_FACT, "error:" + msg);
        }
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private QueryAnalysisResult parseResponse(String raw) {
        if (raw == null || raw.isBlank()) {
            return QueryAnalysisResult.ofFallback(
                    QueryAnalyzerService.QueryType.NORMAL_FACT, "empty llm response");
        }
        try {
            String json = extractJsonObject(raw);
            JsonNode node = JSON.readTree(json);

            String typeStr = node.path("queryType").asText("").trim();
            if (typeStr.isBlank()) {
                return QueryAnalysisResult.ofFallback(
                        QueryAnalyzerService.QueryType.NORMAL_FACT, "missing queryType field");
            }

            QueryAnalyzerService.QueryType queryType;
            try {
                queryType = QueryAnalyzerService.QueryType.valueOf(typeStr);
            } catch (IllegalArgumentException ex) {
                return QueryAnalysisResult.ofFallback(
                        QueryAnalyzerService.QueryType.NORMAL_FACT, "unknown type:" + typeStr);
            }

            double confidence = node.path("confidence").asDouble(0.5);
            if (confidence < minAcceptedConfidence) {
                return QueryAnalysisResult.ofFallback(
                        QueryAnalyzerService.QueryType.NORMAL_FACT,
                        "confidence too low: " + confidence);
            }

            List<String> evidence = new ArrayList<>();
            JsonNode evidenceNode = node.path("evidence");
            if (evidenceNode.isArray()) {
                for (JsonNode item : evidenceNode) {
                    String text = item.asText("").trim();
                    if (!text.isBlank()) {
                        evidence.add(text);
                    }
                }
            }

            return QueryAnalysisResult.ofLlm(queryType, confidence, evidence);

        } catch (Exception ex) {
            return QueryAnalysisResult.ofFallback(
                    QueryAnalyzerService.QueryType.NORMAL_FACT,
                    "parse error: " + ex.getClass().getSimpleName());
        }
    }

    /** Extract the first {...} JSON object from a potentially noisy LLM response. */
    private static String extractJsonObject(String raw) {
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return raw.substring(start, end + 1);
        }
        return raw;
    }
}
