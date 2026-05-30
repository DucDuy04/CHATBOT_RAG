package KLTN.RAG_CHATBOT_BE.rag.rerank;

import KLTN.RAG_CHATBOT_BE.rag.analysis.QueryAnalyzerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link RerankGuard}.
 * No live MySQL / Qdrant / Cohere API required.
 */
class RerankGuardTest {

    private RerankGuard guard;

    @BeforeEach
    void setUp() {
        guard = new RerankGuard();
        ReflectionTestUtils.setField(guard, "guardEnabled", true);
        ReflectionTestUtils.setField(guard, "skipWhenCandidatesLte", 5);
        ReflectionTestUtils.setField(guard, "skipWhenTopScoreGapGte", 0.35);
        ReflectionTestUtils.setField(guard, "skipExactLookupWhenCellScoreGte", 0.80);
    }

    // --- Test 1: candidate count at threshold → skip ---

    @Test
    void candidateCount_atThreshold_skips() {
        RerankGuard.Decision d = guard.decide(
                QueryAnalyzerService.QueryType.NORMAL_FACT,
                5, 0.7, 0.6);

        assertTrue(d.shouldSkip());
        assertEquals(RerankGuard.SkipReason.CANDIDATE_COUNT_LTE_THRESHOLD, d.reason());
        assertEquals(5, d.candidateCount());
    }

    @Test
    void candidateCount_belowThreshold_skips() {
        RerankGuard.Decision d = guard.decide(
                QueryAnalyzerService.QueryType.NORMAL_FACT,
                3, 0.9, 0.1);

        assertTrue(d.shouldSkip());
        assertEquals(RerankGuard.SkipReason.CANDIDATE_COUNT_LTE_THRESHOLD, d.reason());
    }

    // --- Test 2: candidate count above threshold, low gap → no skip ---

    @Test
    void candidateCount_aboveThreshold_lowGap_noSkip() {
        RerankGuard.Decision d = guard.decide(
                QueryAnalyzerService.QueryType.NORMAL_FACT,
                6, 0.6, 0.5);

        assertFalse(d.shouldSkip());
        assertEquals(RerankGuard.SkipReason.NOT_SKIPPED, d.reason());
    }

    // --- Test 3: high score gap → skip ---

    @Test
    void topScoreGap_atThreshold_skips() {
        RerankGuard.Decision d = guard.decide(
                QueryAnalyzerService.QueryType.NORMAL_FACT,
                10, 0.95, 0.60);

        assertTrue(d.shouldSkip());
        assertEquals(RerankGuard.SkipReason.TOP_SCORE_GAP, d.reason());
        assertEquals(0.95 - 0.60, d.topScoreGap(), 1e-6);
    }

    @Test
    void topScoreGap_belowThreshold_noSkip() {
        RerankGuard.Decision d = guard.decide(
                QueryAnalyzerService.QueryType.NORMAL_FACT,
                10, 0.70, 0.50);

        assertFalse(d.shouldSkip());
        assertEquals(RerankGuard.SkipReason.NOT_SKIPPED, d.reason());
    }

    // --- Test 4: guard disabled → never skip regardless of candidates ---

    @Test
    void guardDisabled_doesNotSkip() {
        ReflectionTestUtils.setField(guard, "guardEnabled", false);

        RerankGuard.Decision d = guard.decide(
                QueryAnalyzerService.QueryType.NORMAL_FACT,
                3, 0.99, 0.01);

        assertFalse(d.shouldSkip());
        assertEquals(RerankGuard.SkipReason.NOT_SKIPPED, d.reason());
    }

    // --- Test 5: TABLE_LOOKUP + strong cell score → skip ---

    @Test
    void tableLookup_strongCellScore_skips() {
        RerankGuard.Decision d = guard.decide(
                QueryAnalyzerService.QueryType.TABLE_LOOKUP,
                10, 0.85, 0.60);

        assertTrue(d.shouldSkip());
        assertEquals(RerankGuard.SkipReason.EXACT_LOOKUP_STRONG_CELL_MATCH, d.reason());
    }

    @Test
    void tableLookup_weakCellScore_noSkip() {
        RerankGuard.Decision d = guard.decide(
                QueryAnalyzerService.QueryType.TABLE_LOOKUP,
                10, 0.65, 0.50);

        assertFalse(d.shouldSkip());
        assertEquals(RerankGuard.SkipReason.NOT_SKIPPED, d.reason());
    }

    // --- Test 6: Non-TABLE_LOOKUP with strong score does NOT trigger Rule D ---

    @Test
    void normalFact_strongScore_doesNotTriggerRuleD() {
        RerankGuard.Decision d = guard.decide(
                QueryAnalyzerService.QueryType.NORMAL_FACT,
                10, 0.90, 0.60);

        // gap = 0.30 < 0.35 threshold → Rule B also does not trigger
        assertFalse(d.shouldSkip());
        assertEquals(RerankGuard.SkipReason.NOT_SKIPPED, d.reason());
    }

    // --- Test 7: Rule A fires before Rule B ---

    @Test
    void ruleA_firesBefore_ruleB() {
        // candidates=5 (at threshold) AND gap=0.9 (Rule B would also fire)
        RerankGuard.Decision d = guard.decide(
                QueryAnalyzerService.QueryType.NORMAL_FACT,
                5, 0.99, 0.01);

        assertTrue(d.shouldSkip());
        assertEquals(RerankGuard.SkipReason.CANDIDATE_COUNT_LTE_THRESHOLD, d.reason());
    }

    // --- Test 8: null queryType handled safely ---

    @Test
    void nullQueryType_doesNotCrash() {
        RerankGuard.Decision d = guard.decide(null, 10, 0.5, 0.4);

        assertFalse(d.shouldSkip());
        assertEquals(RerankGuard.SkipReason.NOT_SKIPPED, d.reason());
    }
}
