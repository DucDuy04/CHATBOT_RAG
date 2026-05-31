package KLTN.RAG_CHATBOT_BE.rag.analysis;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Generic, language-independent query intent classifier based on structural signals only.
 *
 * <p>Contract:
 * <ul>
 *   <li>Must NOT contain any domain-specific keyword lists (product names, field names, etc.).</li>
 *   <li>Must NOT contain any language-specific word arrays.</li>
 *   <li>May use structural patterns: section numbers, code-like identifiers, token length.</li>
 *   <li>Returns low confidence when uncertain — lets LLM classifier or fallback take over.</li>
 * </ul>
 *
 * <p>Allowed generic signals:
 * <ol>
 *   <li>Empty / null query → NORMAL_FACT with certainty 1.0</li>
 *   <li>Section-number token {@code X.Y} or {@code X.Y.Z} → SECTION_SUMMARY confidence 0.80</li>
 *   <li>Code-like alphanumeric identifier (uppercase letters + digits, e.g. K46, HK2) →
 *       TABLE_LOOKUP confidence 0.55 (below default trigger threshold — LLM still consulted)</li>
 *   <li>No structural signal → NORMAL_FACT confidence 0.30 (deferred to LLM / fallback)</li>
 * </ol>
 */
@Component
public class LocalGenericQueryAnalyzer {

    /**
     * Numeric section/heading reference: 1.2, 6.2.1, 3.1.4.2
     * Language-independent — section numbering is structural.
     */
    private static final Pattern SECTION_NUMBER = Pattern.compile(
            "\\b\\d{1,3}\\.\\d{1,3}(\\.\\d{1,3})*\\b");

    /**
     * Code-like mixed-case identifier that starts with uppercase letter(s) followed immediately
     * by a digit — matches academic codes like K46, HK2, CNTT21, IT18, but NOT ordinary
     * capitalized words like "Nhóm" (lowercase letters after "N").
     */
    private static final Pattern CODE_IDENTIFIER = Pattern.compile(
            "\\b[A-Z]{1,6}\\d[A-Za-z0-9]{0,10}\\b");

    /** Confidence returned when structural section-number evidence is found. */
    public static final double CONF_SECTION_NUMBER = 0.80;

    /** Confidence returned when code-like identifier found (below threshold — needs LLM). */
    public static final double CONF_CODE_IDENT = 0.55;

    /** Confidence returned when no strong structural signal found. */
    public static final double CONF_NO_SIGNAL = 0.30;

    /**
     * Classify {@code question} using structural signals only.
     *
     * @param question original question text (may contain diacritics, mixed case)
     * @return analysis result; never null
     */
    public QueryAnalysisResult analyze(String question) {
        if (question == null || question.isBlank()) {
            return QueryAnalysisResult.ofLocal(
                    QueryAnalyzerService.QueryType.NORMAL_FACT, 1.0, List.of("empty query"));
        }

        // Signal 1: section/chapter number pattern — structural, language-independent
        if (SECTION_NUMBER.matcher(question).find()) {
            return QueryAnalysisResult.ofLocal(
                    QueryAnalyzerService.QueryType.SECTION_SUMMARY, CONF_SECTION_NUMBER,
                    List.of("section number pattern detected"));
        }

        // Signal 2: code-like identifier (e.g. course code, cohort code)
        // Low confidence — does not commit; LLM classifier will confirm in active mode
        if (CODE_IDENTIFIER.matcher(question).find()) {
            return QueryAnalysisResult.ofLocal(
                    QueryAnalyzerService.QueryType.TABLE_LOOKUP, CONF_CODE_IDENT,
                    List.of("code-like identifier detected"));
        }

        // No structural signal — uncertain; defer to LLM or default fallback
        return QueryAnalysisResult.ofLocal(
                QueryAnalyzerService.QueryType.NORMAL_FACT, CONF_NO_SIGNAL,
                List.of("no structural signal"));
    }
}
