package KLTN.RAG_CHATBOT_BE.service;

import KLTN.RAG_CHATBOT_BE.dto.TokenUsageDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagTokenAuditTest {

    @AfterEach
    void cleanup() {
        RagTokenAudit.clear();
    }

    @Test
    void estimateTokens_ceilCharsOverFour() {
        assertEquals(0, RagTokenAudit.estimateTokens(0));
        assertEquals(1, RagTokenAudit.estimateTokens(1));
        assertEquals(1, RagTokenAudit.estimateTokens(4));
        assertEquals(2, RagTokenAudit.estimateTokens(5));
        assertEquals(250, RagTokenAudit.estimateTokens(1000));
    }

    @Test
    void estimatedTotalRequestTokens_sumsInputAndReserved() {
        assertEquals(4327, RagTokenAudit.estimatedTotalRequestTokens(3303, 1024));
    }

    @Test
    void parseProviderRequestedTokens_fromGroqMessage() {
        String message = "Request too large for model `llama-3.1-8b-instant` "
                + "on tokens per minute (TPM): Limit 6000, Requested 22307 tokens, please reduce";
        assertEquals(22307, RagTokenAudit.parseProviderRequestedTokens(message));
    }

    @Test
    void finish_emitsTokenUsageDtoWithEstimates() {
        RagTokenAudit.begin(RagTokenAudit.Mode.PLAYGROUND, UUID.randomUUID(), "sess-1");
        RagTokenAudit.recordPreLlm(
                "llama-3.3-70b-versatile",
                0.2,
                1024,
                5,
                5,
                "system",
                java.util.List.of(),
                6922,
                "question?",
                "user-prompt-body"
        );

        TokenUsageDto usage = RagTokenAudit.finish(true);

        assertNotNull(usage);
        assertTrue(usage.getEstimatedInputTokens() > 0);
        assertEquals(1024, usage.getReservedOutputTokens());
        assertEquals(
                usage.getEstimatedInputTokens() + 1024,
                usage.getEstimatedTotalRequestTokens()
        );
        assertEquals(5, usage.getFinalContexts());
        assertEquals(6922, usage.getContextChars());
        assertNull(usage.getActualPromptTokens());
        assertEquals(RagTokenAudit.PROVIDER_GROQ, usage.getProvider());
    }

    @Test
    void recordLlmFailure_parsesProviderRequestedTokens() {
        RagTokenAudit.begin(RagTokenAudit.Mode.COMPARE_A, UUID.randomUUID(), null);
        RagTokenAudit.recordPreLlm("m", 0.2, 512, 5, 5, "sys", java.util.List.of(), 100, "q", "p");
        Exception error = new RuntimeException(
                "rate_limit_exceeded: Requested 31101 tokens, Limit 6000"
        );
        RagTokenAudit.recordLlmFailure(error, "qwen/qwen3-32b", 2);

        TokenUsageDto usage = RagTokenAudit.finish(false);

        assertNotNull(usage);
        assertEquals(31101, usage.getProviderRequestedTokens());
        assertEquals("rate_limit_exceeded", usage.getErrorCode());
        assertEquals(Boolean.FALSE, usage.getSuccess());
        assertEquals(Boolean.TRUE, usage.getCompareMode());
    }

    @Test
    void tokenUsageLogLine_doesNotContainApiKey() {
        RagTokenAudit.begin(RagTokenAudit.Mode.PLAYGROUND, UUID.randomUUID(), "s");
        RagTokenAudit.recordPreLlm("model", 0.1, 100, 3, 3, "prompt", java.util.List.of(), 10, "q", "p");
        TokenUsageDto usage = RagTokenAudit.finish(true);
        assertNotNull(usage);
        String serialized = usage.toString();
        assertTrue(serialized == null || !serialized.toLowerCase().contains("gsk_"));
        assertTrue(serialized == null || !serialized.toLowerCase().contains("api-key"));
    }
}
