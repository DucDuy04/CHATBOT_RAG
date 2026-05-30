package KLTN.RAG_CHATBOT_BE.rag.retrieve;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link QueryVariantDedupe}.
 * No Spring / MySQL / Qdrant required.
 *
 * Covers task 27D acceptance criteria:
 *  - Exact normalized-text duplicates are skipped.
 *  - Numeric/scope tokens are preserved (K45 vs K46, HK1 vs HK2).
 *  - Group tokens are preserved (Nhóm 4 vs Nhóm 2).
 *  - Disabled config preserves all variants.
 *  - Different filter keys prevent dedupe.
 */
class QueryVariantDedupeTest {

    private static final String SCOPE = "widget-123";
    private static final String MODE  = "VECTOR";

    // ─────────────────────────────────────────────────────────────────
    // Test 1 — exact normalized duplicate skipped
    // ─────────────────────────────────────────────────────────────────

    @Test
    void exactNormalizedDuplicate_isSkipped() {
        List<String> variants = List.of(
                "Kỹ năng mềm Nhóm 4",
                "kỹ năng   mềm nhóm 4"
        );

        QueryVariantDedupe.DedupeResult result =
                QueryVariantDedupe.dedupe(variants, true, SCOPE, MODE);

        assertEquals(2, result.total());
        assertEquals(1, result.unique());
        assertEquals(1, result.skipped());

        QueryVariantDedupe.QueryVariant skipped = result.allVariants().get(1);
        assertTrue(skipped.skipped());
        assertEquals(QueryVariantDedupe.SkipReason.NORMALIZED_TEXT_DUPLICATE, skipped.skipReason());
        assertEquals("Kỹ năng mềm Nhóm 4", skipped.representedBy());
    }

    @Test
    void trailingWhitespace_andCaseDifference_treatedAsDuplicate() {
        List<String> variants = List.of(
                "  Kiến trúc K46  ",
                "kiến trúc k46"
        );

        QueryVariantDedupe.DedupeResult result =
                QueryVariantDedupe.dedupe(variants, true, SCOPE, MODE);

        assertEquals(1, result.unique());
        assertEquals(1, result.skipped());
    }

    // ─────────────────────────────────────────────────────────────────
    // Test 2 — numeric/scope distinction preserved
    // ─────────────────────────────────────────────────────────────────

    @Test
    void numericDistinction_K46_vs_K45_preserved() {
        List<String> variants = List.of(
                "Kiến trúc K46 học kỳ 2",
                "Kiến trúc K45 học kỳ 2",
                "Kiến trúc K46 học kỳ 1"
        );

        QueryVariantDedupe.DedupeResult result =
                QueryVariantDedupe.dedupe(variants, true, SCOPE, MODE);

        assertEquals(3, result.total());
        assertEquals(3, result.unique());
        assertEquals(0, result.skipped());
    }

    // ─────────────────────────────────────────────────────────────────
    // Test 3 — group distinction preserved
    // ─────────────────────────────────────────────────────────────────

    @Test
    void groupDistinction_Nhom4_vs_Nhom2_preserved() {
        List<String> variants = List.of(
                "Kỹ năng mềm Nhóm 4",
                "Kỹ năng mềm Nhóm 2"
        );

        QueryVariantDedupe.DedupeResult result =
                QueryVariantDedupe.dedupe(variants, true, SCOPE, MODE);

        assertEquals(2, result.total());
        assertEquals(2, result.unique());
        assertEquals(0, result.skipped());
    }

    // ─────────────────────────────────────────────────────────────────
    // Test 4 — disabled config preserves all variants
    // ─────────────────────────────────────────────────────────────────

    @Test
    void dedupeDisabled_preservesAllVariants() {
        List<String> variants = List.of(
                "Kỹ năng mềm Nhóm 4",
                "kỹ năng mềm nhóm 4",
                "kỹ năng   mềm nhóm 4"
        );

        QueryVariantDedupe.DedupeResult result =
                QueryVariantDedupe.dedupe(variants, false, SCOPE, MODE);

        assertEquals(3, result.total());
        assertEquals(3, result.unique(),
                "When disabled, every variant must be kept regardless of normalization");
        assertEquals(0, result.skipped());
        result.allVariants().forEach(qv ->
                assertEquals(QueryVariantDedupe.SkipReason.DEDUPE_DISABLED, qv.skipReason()));
    }

    // ─────────────────────────────────────────────────────────────────
    // Test 5 — different scope filter keys are NOT deduped
    // ─────────────────────────────────────────────────────────────────

    @Test
    void differentScopeFilterKeys_areNotDeduped() {
        // Same normalized text, different scope (e.g., two widgetIds)
        // Simulate by calling dedupe twice with different scopes and verifying
        // the dedupe key differs when scope differs.

        String text = "Kỹ năng mềm Nhóm 4";
        String key1 = QueryVariantDedupe.normalizeKey(text, "widget-aaa", MODE);
        String key2 = QueryVariantDedupe.normalizeKey(text, "widget-bbb", MODE);

        assertNotEquals(key1, key2,
                "Same text but different scopeFilterKey must produce different dedupe keys");
    }

    @Test
    void differentScopeFilterKeys_sameText_keptSeparateInSingleCallWithDifferentModes() {
        // Simulate: same text, different modes → different keys
        String text = "Kỹ năng mềm Nhóm 4";
        String keyVector  = QueryVariantDedupe.normalizeKey(text, SCOPE, "VECTOR");
        String keyKeyword = QueryVariantDedupe.normalizeKey(text, SCOPE, "KEYWORD");

        assertNotEquals(keyVector, keyKeyword,
                "Same text but different searchModeKey must produce different dedupe keys");
    }

    // ─────────────────────────────────────────────────────────────────
    // Normalization correctness
    // ─────────────────────────────────────────────────────────────────

    @Test
    void normalizeText_trimsAndCollapses() {
        assertEquals("kỹ năng mềm nhóm 4",
                QueryVariantDedupe.normalizeText("  Kỹ năng mềm   Nhóm 4  "));
    }

    @Test
    void normalizeText_stripsControlChars() {
        // Zero-width space U+200B should be stripped
        String withZWS = "Kỹ\u200Bnăng mềm";
        assertEquals("kỹnăng mềm", QueryVariantDedupe.normalizeText(withZWS));
    }

    @Test
    void normalizeText_nullReturnsEmpty() {
        assertEquals("", QueryVariantDedupe.normalizeText(null));
    }

    @Test
    void emptyVariantList_returnsEmptyResult() {
        QueryVariantDedupe.DedupeResult result =
                QueryVariantDedupe.dedupe(List.of(), true, SCOPE, MODE);

        assertEquals(0, result.total());
        assertEquals(0, result.unique());
        assertEquals(0, result.skipped());
    }

    @Test
    void singleVariant_neverSkipped() {
        QueryVariantDedupe.DedupeResult result =
                QueryVariantDedupe.dedupe(List.of("Kiến trúc K46"), true, SCOPE, MODE);

        assertEquals(1, result.total());
        assertEquals(1, result.unique());
        assertEquals(0, result.skipped());
        assertFalse(result.uniqueVariants().get(0).skipped());
    }

    @Test
    void firstOccurrenceIsKept_notSkipped() {
        List<String> variants = List.of("A", "a", "  A  ");

        QueryVariantDedupe.DedupeResult result =
                QueryVariantDedupe.dedupe(variants, true, SCOPE, MODE);

        assertEquals(1, result.unique());
        // First "A" is kept, "a" and "  A  " are skipped
        assertFalse(result.allVariants().get(0).skipped());
        assertTrue(result.allVariants().get(1).skipped());
        assertTrue(result.allVariants().get(2).skipped());
        assertEquals("A", result.allVariants().get(1).representedBy());
    }
}
