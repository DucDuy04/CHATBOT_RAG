package KLTN.RAG_CHATBOT_BE.rag.retrieve;

import KLTN.RAG_CHATBOT_BE.domain.document.DocumentChunk;
import KLTN.RAG_CHATBOT_BE.ingest.normalize.NormalizedTableService;
import KLTN.RAG_CHATBOT_BE.rag.analysis.QuerySignalExtractor;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Cell-aware scoring for normalized rows using query/runtime table data only.
 */
@Slf4j
public final class CellAwareTableRowScorer {

    private static final ObjectMapper JSON = new ObjectMapper();

    static final double EXACT_IDENTIFIER_CELL = 8.0;
    static final double EXACT_LABEL_CELL = 6.0;
    /** Value present in cell but header key does not semantically match label type (e.g. col_N headers). */
    static final double VALUE_COVERAGE_CELL = 4.0;
    static final double MULTI_SIGNAL_SAME_ROW = 5.0;
    static final double PHRASE_CELL = 2.0;
    static final double COLUMN_INTENT = 1.5;
    static final double GROUP_CONTEXT = 0.8;
    static final double TABLE_NAME = 0.5;
    static final double MISMATCH_PENALTY = -8.0;

    private CellAwareTableRowScorer() {}

    public record CellAwareScore(
            double total,
            double exactIdentifierCellScore,
            double exactLabelCellScore,
            double phraseCellScore,
            double multiSignalSameRowBoost,
            double columnNameIntentBoost,
            double groupContextScore,
            double tableNameScore,
            double mismatchPenalty,
            int matchedSignalCategories
    ) {}

    public record ParsedStructuredLabel(String raw, String type, String value) {}

    public static CellAwareScore score(
            DocumentChunk chunk,
            QuerySignalExtractor.QuerySignals signals,
            String question
    ) {
        if (chunk == null || signals == null) {
            return zero();
        }
        String type = chunk.getChunkType() == null ? "text" : chunk.getChunkType();
        if (!"normalized_table_row".equals(type)) {
            return zero();
        }

        Map<String, String> cells = parseCells(chunk);
        if (cells.isEmpty()) {
            return zero();
        }

        double exactId = 0.0;
        double exactLabel = 0.0;
        double phrase = 0.0;
        double mismatch = 0.0;
        Set<String> matchedCategories = new LinkedHashSet<>();

        for (String id : signals.identifiers()) {
            if (cellContainsExactIdentifier(cells, id)) {
                exactId += EXACT_IDENTIFIER_CELL;
                matchedCategories.add("identifier");
            }
        }

        List<ParsedStructuredLabel> labels = parseLabels(signals.structuredLabels());
        for (ParsedStructuredLabel label : labels) {
            boolean matched = false;
            boolean conflict = false;
            boolean valueCovered = false;
            for (Map.Entry<String, String> cell : cells.entrySet()) {
                if (cellMatchesLabel(cell.getKey(), cell.getValue(), label)) {
                    exactLabel += EXACT_LABEL_CELL;
                    matched = true;
                    matchedCategories.add("label");
                } else if (cellConflictsWithLabel(cell.getKey(), cell.getValue(), label)) {
                    conflict = true;
                } else if (!valueCovered && valueCoverageMatch(cell.getValue(), label)) {
                    valueCovered = true;
                }
            }
            if (!matched) {
                if (valueCovered) {
                    exactLabel += VALUE_COVERAGE_CELL;
                    matchedCategories.add("label");
                } else if (conflict) {
                    mismatch += MISMATCH_PENALTY;
                }
            }
        }

        for (String ngram : signals.ngrams()) {
            if (ngram.length() < 4) {
                continue;
            }
            for (String value : cells.values()) {
                if (cellValueContainsPhrase(value, ngram)) {
                    phrase += PHRASE_CELL * Math.min(3.0, ngram.split("\\s+").length * 0.5);
                    matchedCategories.add("phrase");
                    break;
                }
            }
        }

        for (String token : signals.contentTokens()) {
            if (token.length() < 3 || QuerySignalExtractor.isStructuredValueToken(token)) {
                continue;
            }
            for (String value : cells.values()) {
                if (boundaryTokenEquals(QuerySignalExtractor.normalize(value), token)) {
                    phrase += 1.0;
                    matchedCategories.add("term");
                    break;
                }
            }
        }

        for (String d : signals.dates()) {
            for (String value : cells.values()) {
                if (value != null && value.toLowerCase(Locale.ROOT).contains(d.toLowerCase(Locale.ROOT))) {
                    matchedCategories.add("temporal");
                    exactId += 1.5;
                    break;
                }
            }
        }

        double multiBoost = matchedCategories.size() >= 2
                ? MULTI_SIGNAL_SAME_ROW * (matchedCategories.size() - 1)
                : 0.0;
        double columnIntent = columnIntentBoost(cells, question);
        double groupCtx = groupContextBoost(chunk, signals, labels);
        double tableName = tableNameBoost(chunk, signals);
        double total = exactId + exactLabel + phrase + multiBoost + columnIntent + groupCtx + tableName + mismatch;

        return new CellAwareScore(
                Math.max(0.0, total),
                exactId, exactLabel, phrase, multiBoost, columnIntent, groupCtx, tableName, mismatch,
                matchedCategories.size());
    }

    public static Map<String, String> parseCells(DocumentChunk chunk) {
        if (chunk == null) {
            return Map.of();
        }
        String json = chunk.getCellsJson();
        if (json == null || json.isBlank() || "{}".equals(json.trim())) {
            return Map.of();
        }
        try {
            Map<String, String> parsed = JSON.readValue(json, new TypeReference<LinkedHashMap<String, String>>() {});
            return parsed == null ? Map.of() : parsed;
        } catch (Exception e) {
            log.debug("[CellAware] structured cells parse failed chunkId={}: {}", chunk.getId(), e.getMessage());
            return Map.of();
        }
    }

    static Map<String, String> parseCellsFromCanonical(String content) {
        return Map.of();
    }

    static boolean cellContainsExactIdentifier(Map<String, String> cells, String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return false;
        }
        String normId = QuerySignalExtractor.normalize(identifier);
        for (String value : cells.values()) {
            if (boundaryTokenEquals(QuerySignalExtractor.normalize(value), normId)) {
                return true;
            }
        }
        return false;
    }

    static boolean cellMatchesLabel(String cellKey, String cellValue, ParsedStructuredLabel label) {
        if (label == null || label.value() == null) {
            return false;
        }
        String prefix = QuerySignalExtractor.normalize(label.type());
        String value = QuerySignalExtractor.normalize(label.value());
        if (headerPrefixSimilarity(prefix, cellKey) < 0.72) {
            return false;
        }
        return valueMatchesBoundary(QuerySignalExtractor.normalize(cellValue), prefix, value);
    }

    static boolean cellConflictsWithLabel(String cellKey, String cellValue, ParsedStructuredLabel label) {
        if (label == null || label.value() == null) {
            return false;
        }
        String prefix = QuerySignalExtractor.normalize(label.type());
        if (headerPrefixSimilarity(prefix, cellKey) < 0.72) {
            return false;
        }
        String value = QuerySignalExtractor.normalize(label.value());
        String normalizedCellValue = QuerySignalExtractor.normalize(cellValue);
        return !valueMatchesBoundary(normalizedCellValue, prefix, value)
                && containsConflictingNumericValue(normalizedCellValue, value);
    }

    static boolean valueMatchesBoundary(String normalizedText, String labelType, String labelValue) {
        if (normalizedText == null || labelValue == null || labelValue.isBlank()) {
            return false;
        }
        String value = QuerySignalExtractor.normalize(labelValue);
        String text = QuerySignalExtractor.normalize(normalizedText);
        return boundaryTokenEquals(text, value);
    }

    /**
     * Value-level coverage: the label value appears in the cell (boundary match)
     * regardless of whether the column key semantically matches the label type.
     * Used as a fallback when headers are generic (col_N) and header-prefix
     * similarity is too low for full label matching.
     */
    static boolean valueCoverageMatch(String cellValue, ParsedStructuredLabel label) {
        if (label == null || label.value() == null || label.value().isBlank()) {
            return false;
        }
        if (cellValue == null || cellValue.isBlank()) {
            return false;
        }
        String normLabelValue = QuerySignalExtractor.normalize(label.value());
        if (normLabelValue.isBlank()) {
            return false;
        }
        String normCell = QuerySignalExtractor.normalize(cellValue);
        return boundaryTokenEquals(normCell, normLabelValue);
    }

    private static boolean boundaryTokenEquals(String normalizedText, String normalizedNeedle) {
        if (normalizedText == null || normalizedNeedle == null || normalizedNeedle.isBlank()) {
            return false;
        }
        if (normalizedText.equals(normalizedNeedle)) {
            return true;
        }
        Pattern p = Pattern.compile("(?<![\\p{L}\\p{N}])" + Pattern.quote(normalizedNeedle)
                + "(?![\\p{L}\\p{N}])", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS);
        return p.matcher(normalizedText).find();
    }

    static boolean containsConflictingNumericValue(String normalizedCellValue, String labelValue) {
        if (labelValue == null || !labelValue.matches("\\d{1,4}[a-z]?")) {
            return false;
        }
        Matcher m = Pattern.compile("\\d{1,4}[a-z]?").matcher(normalizedCellValue);
        while (m.find()) {
            if (!m.group().equals(labelValue)) {
                return true;
            }
        }
        return false;
    }

    static boolean keyMatchesLabelType(String normKey, String normType) {
        return headerPrefixSimilarity(normType, normKey) >= 0.72;
    }

    static boolean cellValueContainsPhrase(String cellValue, String ngram) {
        if (cellValue == null || ngram == null) {
            return false;
        }
        return QuerySignalExtractor.normalize(cellValue).contains(ngram);
    }

    static double columnIntentBoost(Map<String, String> cells, String question) {
        if (cells == null || cells.isEmpty() || question == null || question.isBlank()) {
            return 0.0;
        }
        Set<String> queryNgrams = new LinkedHashSet<>(
                QuerySignalExtractor.buildNgrams(QuerySignalExtractor.normalize(question), 1, 3));
        double boost = 0.0;
        for (String colKey : cells.keySet()) {
            String normalizedKey = QuerySignalExtractor.normalize(colKey);
            if (normalizedKey.isBlank()) {
                continue;
            }
            for (String q : queryNgrams) {
                double similarity = headerPrefixSimilarity(q, normalizedKey);
                if (similarity >= 0.86) {
                    boost += COLUMN_INTENT * similarity;
                    break;
                }
            }
        }
        return boost;
    }

    static double groupContextBoost(DocumentChunk chunk,
                                    QuerySignalExtractor.QuerySignals signals,
                                    List<ParsedStructuredLabel> labels) {
        String ctx = chunk.getGroupContext();
        if (ctx == null || ctx.isBlank()) {
            return 0.0;
        }
        String normCtx = QuerySignalExtractor.normalize(ctx);
        double boost = 0.0;
        for (ParsedStructuredLabel label : labels) {
            String raw = QuerySignalExtractor.normalize(label.raw());
            String value = QuerySignalExtractor.normalize(label.value());
            if ((!raw.isBlank() && normCtx.contains(raw)) || boundaryTokenEquals(normCtx, value)) {
                boost += GROUP_CONTEXT;
            }
        }
        for (String ngram : signals.ngrams()) {
            if (ngram.length() >= 4 && normCtx.contains(ngram)) {
                boost += 0.3;
            }
        }
        return boost;
    }

    static double tableNameBoost(DocumentChunk chunk, QuerySignalExtractor.QuerySignals signals) {
        String name = chunk.getTableName();
        if (name == null || name.isBlank()) {
            return 0.0;
        }
        String normName = QuerySignalExtractor.normalize(name);
        for (String ngram : signals.ngrams()) {
            if (ngram.length() >= 5 && normName.contains(ngram)) {
                return TABLE_NAME;
            }
        }
        return 0.0;
    }

    static List<ParsedStructuredLabel> parseLabels(List<String> structuredLabels) {
        if (structuredLabels == null || structuredLabels.isEmpty()) {
            return List.of();
        }
        List<ParsedStructuredLabel> out = new ArrayList<>();
        for (String raw : structuredLabels) {
            ParsedStructuredLabel p = parseLabel(raw);
            if (p != null) {
                out.add(p);
            }
        }
        return out;
    }

    static ParsedStructuredLabel parseLabel(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalized = QuerySignalExtractor.normalize(raw);
        int split = normalized.lastIndexOf(' ');
        if (split <= 0 || split >= normalized.length() - 1) {
            return null;
        }
        String prefix = normalized.substring(0, split).trim();
        String value = normalized.substring(split + 1).trim();
        if (prefix.isBlank() || !QuerySignalExtractor.isStructuredValueToken(value)) {
            return null;
        }
        return new ParsedStructuredLabel(raw.trim(), prefix, value);
    }

    public static boolean isCompareQuery(String question, QuerySignalExtractor.QuerySignals signals) {
        return signals != null && parseLabels(signals.structuredLabels()).size() >= 2;
    }

    static boolean isTableLikeSummary(DocumentChunk chunk) {
        String type = chunk.getChunkType();
        if (!"section_summary".equals(type) && !"parent_section_summary".equals(type)) {
            return false;
        }
        return NormalizedTableService.hasHighTableLikeDensity(
                Optional.ofNullable(chunk.getContent()).orElse(""));
    }

    static boolean hasExactCellMatch(DocumentChunk chunk, QuerySignalExtractor.QuerySignals signals) {
        if (chunk == null || signals == null) {
            return false;
        }
        CellAwareScore s = score(chunk, signals, "");
        return s.exactIdentifierCellScore() > 0 || s.exactLabelCellScore() > 0;
    }

    static double headerPrefixSimilarity(String queryPrefix, String columnKey) {
        String a = QuerySignalExtractor.normalize(queryPrefix);
        String b = QuerySignalExtractor.normalize(columnKey);
        if (a.isBlank() || b.isBlank()) {
            return 0.0;
        }
        if (a.equals(b)) {
            return 1.0;
        }
        Set<String> at = tokens(a);
        Set<String> bt = tokens(b);
        Set<String> intersection = new LinkedHashSet<>(at);
        intersection.retainAll(bt);
        if (!intersection.isEmpty()) {
            return intersection.size() / (double) Math.min(at.size(), bt.size());
        }
        Set<String> ag = charBigrams(a);
        Set<String> bg = charBigrams(b);
        if (ag.isEmpty() || bg.isEmpty()) {
            return 0.0;
        }
        Set<String> gi = new LinkedHashSet<>(ag);
        gi.retainAll(bg);
        Set<String> gu = new LinkedHashSet<>(ag);
        gu.addAll(bg);
        return gi.size() / (double) gu.size();
    }

    private static Set<String> tokens(String value) {
        Set<String> out = new LinkedHashSet<>();
        for (String t : value.split("\\s+")) {
            if (!t.isBlank()) {
                out.add(t);
            }
        }
        return out;
    }

    private static Set<String> charBigrams(String value) {
        Set<String> out = new LinkedHashSet<>();
        String compact = value.replaceAll("\\s+", "");
        for (int i = 0; i < compact.length() - 1; i++) {
            out.add(compact.substring(i, i + 2));
        }
        return out;
    }

    private static CellAwareScore zero() {
        return new CellAwareScore(0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }
}
