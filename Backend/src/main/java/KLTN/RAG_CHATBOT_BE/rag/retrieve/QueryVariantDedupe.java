package KLTN.RAG_CHATBOT_BE.rag.retrieve;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Conservative query-variant deduplication for the RAG retrieval pipeline.
 *
 * <p>Reduces redundant Qdrant vector searches when multiple query variants
 * produced by {@code QueryAnalyzerService.rewriteQuery()} map to the same
 * normalized text.
 *
 * <p>Only exact normalized-text dedupe is implemented (task 27D).
 * Cosine-similarity dedupe is explicitly deferred to a future task (27E+)
 * because it requires embeddings and carries recall risk at any threshold
 * below ~0.99.
 *
 * <h3>Normalization rules (conservative)</h3>
 * <ul>
 *   <li>Trim leading/trailing whitespace</li>
 *   <li>Lowercase (Locale-independent, Unicode-safe)</li>
 *   <li>Collapse repeated whitespace (any Unicode whitespace → single ASCII space)</li>
 *   <li>NFC Unicode normalization (matches {@code QueryAnalyzerService.normalize()})</li>
 *   <li>Strip invisible/control characters (U+0000–U+001F, U+007F, U+200B, U+FEFF)</li>
 * </ul>
 *
 * <p>NOT performed: digit removal, punctuation stripping, stemming, synonym expansion.
 * This keeps numeric labels like "K46", "Nhóm 4", "HK2" distinct.
 *
 * <h3>Dedupe key</h3>
 * <pre>
 *   normalizedText + "|" + scopeFilterKey + "|" + searchModeKey
 * </pre>
 * Variants with different scope/mode are never considered duplicates even if
 * the text normalizes identically.
 */
public final class QueryVariantDedupe {

    /** Reason a variant was kept or skipped. */
    public enum SkipReason {
        NOT_SKIPPED,
        NORMALIZED_TEXT_DUPLICATE,
        DEDUPE_DISABLED
    }

    /**
     * Represents one query variant after dedupe analysis.
     *
     * @param originalText   the original variant string as produced by rewriteQuery()
     * @param normalizedKey  full composite dedupe key (normalizedText|filter|mode)
     * @param skipped        true if this variant was classified as a duplicate
     * @param skipReason     reason for the skip decision
     * @param representedBy  originalText of the representative kept variant (null when not skipped)
     */
    public record QueryVariant(
            String originalText,
            String normalizedKey,
            boolean skipped,
            SkipReason skipReason,
            String representedBy
    ) {}

    /**
     * Result of {@link #dedupe}.
     *
     * @param uniqueVariants  variants to actually search (skipped ones excluded)
     * @param allVariants     all variants including skipped, in original order
     * @param total           total input count
     * @param unique          unique (kept) count
     * @param skipped         skipped (duplicate) count
     */
    public record DedupeResult(
            List<QueryVariant> uniqueVariants,
            List<QueryVariant> allVariants,
            int total,
            int unique,
            int skipped
    ) {}

    private QueryVariantDedupe() {}

    /**
     * Deduplicates query variants using normalized-text hash matching.
     *
     * @param variants       raw variants from rewriteQuery(); must not be null
     * @param enabled        if false, returns all variants with DEDUPE_DISABLED reason
     * @param scopeFilterKey stable string representing the Qdrant scope filter
     *                       (e.g., widgetId.toString()); variants with different scope keys
     *                       are never considered duplicates
     * @param searchModeKey  stable string representing the search mode
     *                       (e.g., "VECTOR"); variants with different modes are never
     *                       considered duplicates
     */
    public static DedupeResult dedupe(
            List<String> variants,
            boolean enabled,
            String scopeFilterKey,
            String searchModeKey) {

        if (variants == null || variants.isEmpty()) {
            return new DedupeResult(List.of(), List.of(), 0, 0, 0);
        }

        if (!enabled) {
            List<QueryVariant> all = variants.stream()
                    .map(v -> new QueryVariant(v, normalizeKey(v, scopeFilterKey, searchModeKey),
                            false, SkipReason.DEDUPE_DISABLED, null))
                    .toList();
            return new DedupeResult(all, all, all.size(), all.size(), 0);
        }

        // LinkedHashMap preserves first-occurrence order
        Map<String, String> seenKeyToRepresentative = new LinkedHashMap<>();
        List<QueryVariant> allVariants = new ArrayList<>(variants.size());
        List<QueryVariant> uniqueVariants = new ArrayList<>();

        for (String v : variants) {
            String key = normalizeKey(v, scopeFilterKey, searchModeKey);
            if (seenKeyToRepresentative.containsKey(key)) {
                String representative = seenKeyToRepresentative.get(key);
                QueryVariant qv = new QueryVariant(v, key, true,
                        SkipReason.NORMALIZED_TEXT_DUPLICATE, representative);
                allVariants.add(qv);
            } else {
                seenKeyToRepresentative.put(key, v);
                QueryVariant qv = new QueryVariant(v, key, false, SkipReason.NOT_SKIPPED, null);
                allVariants.add(qv);
                uniqueVariants.add(qv);
            }
        }

        int skippedCount = allVariants.size() - uniqueVariants.size();
        return new DedupeResult(
                List.copyOf(uniqueVariants),
                List.copyOf(allVariants),
                allVariants.size(),
                uniqueVariants.size(),
                skippedCount);
    }

    /**
     * Builds composite dedupe key: {@code normalizedText|scopeFilterKey|searchModeKey}.
     * Public for testing.
     */
    public static String normalizeKey(String text, String scopeFilterKey, String searchModeKey) {
        String norm = normalizeText(text);
        String scope = scopeFilterKey == null ? "" : scopeFilterKey;
        String mode  = searchModeKey  == null ? "" : searchModeKey;
        return norm + "|" + scope + "|" + mode;
    }

    /**
     * Conservative text normalization.
     * Visible for testing.
     */
    public static String normalizeText(String text) {
        if (text == null) return "";
        // NFC normalization — same as QueryAnalyzerService.normalize()
        String nfc = Normalizer.normalize(text, Normalizer.Form.NFC);
        // Strip invisible/control chars (keeps regular Unicode letters/digits/punctuation)
        String noControl = nfc.replaceAll("[\\p{Cc}\\p{Cf}&&[^\\t\\n\\r ]]", "");
        // Trim + collapse whitespace + lowercase
        return noControl.strip()
                .replaceAll("\\s+", " ")
                .toLowerCase(java.util.Locale.ROOT);
    }
}
