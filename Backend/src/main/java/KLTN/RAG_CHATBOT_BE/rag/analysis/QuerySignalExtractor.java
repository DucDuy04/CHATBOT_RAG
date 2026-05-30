package KLTN.RAG_CHATBOT_BE.rag.analysis;

import java.text.Normalizer;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Generic query signal extraction for hybrid keyword search.
 */
public final class QuerySignalExtractor {

    private QuerySignalExtractor() {}

    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile(
            "\\b([A-Za-z]{1,8}[0-9][A-Za-z0-9\\-]{1,20}|[0-9]{2,}[A-Za-z][A-Za-z0-9\\-]{0,20}|[A-Z]{2,}[0-9]{2,}[A-Za-z0-9\\-]*)\\b");

    private static final Pattern DATE_DMY = Pattern.compile(
            "\\b(\\d{1,2}[/.\\-]\\d{1,2}[/.\\-]\\d{2,4})\\b");
    private static final Pattern DATE_ISO = Pattern.compile(
            "\\b(\\d{4}[/.\\-]\\d{1,2}[/.\\-]\\d{1,2})\\b");
    private static final Pattern DATE_DM = Pattern.compile(
            "\\b(\\d{1,2}[/.\\-]\\d{1,2})\\b");

    private static final Pattern NUMERIC_TOKEN = Pattern.compile(
            "\\b\\d{1,4}(?:[.,]\\d+)?%?\\b");

    public record QuerySignals(
            List<String> identifiers,
            List<String> dates,
            List<String> numbers,
            List<String> structuredLabels,
            List<String> ngrams,
            List<String> contentTokens
    ) {}

    public static QuerySignals extract(String query) {
        if (query == null || query.isBlank()) {
            return new QuerySignals(List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        }
        String raw = query.trim();
        String normalized = normalize(raw);

        List<String> identifiers = extractIdentifiers(raw);
        List<String> dates = extractDates(raw);
        List<String> numbers = extractNumbers(normalized);
        List<String> labels = extractStructuredLabels(raw);
        List<String> ngrams = buildNgrams(normalized, 2, 5);
        List<String> contentTokens = extractContentTokens(normalized);

        return new QuerySignals(identifiers, dates, numbers, labels, ngrams, contentTokens);
    }

    public static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String n = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace('\u0111', 'd')
                .replace('\u0110', 'D')
                .toLowerCase(Locale.ROOT);
        return n.replaceAll("[\\p{Punct}&&[^/.\\-]]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static List<String> extractIdentifiers(String raw) {
        Set<String> ids = new LinkedHashSet<>();
        Matcher m = IDENTIFIER_PATTERN.matcher(raw);
        while (m.find()) {
            String id = m.group(1).trim();
            if (id.length() >= 4) {
                ids.add(id);
            }
        }
        return List.copyOf(ids);
    }

    private static List<String> extractDates(String raw) {
        Set<String> dates = new LinkedHashSet<>();
        for (Pattern p : List.of(DATE_DMY, DATE_ISO, DATE_DM)) {
            Matcher m = p.matcher(raw);
            while (m.find()) {
                dates.add(m.group(1).trim());
            }
        }
        return List.copyOf(dates);
    }

    private static List<String> extractNumbers(String normalized) {
        Set<String> nums = new LinkedHashSet<>();
        Matcher m = NUMERIC_TOKEN.matcher(normalized);
        while (m.find()) {
            String n = m.group().trim();
            if (!n.isBlank()) {
                nums.add(n);
            }
        }
        return List.copyOf(nums);
    }

    private static List<String> extractStructuredLabels(String raw) {
        Set<String> labels = new LinkedHashSet<>();
        String normalized = normalize(raw);
        if (normalized.isBlank()) {
            return List.of();
        }
        String[] tokens = normalized.split("\\s+");
        for (int i = 1; i < tokens.length; i++) {
            String value = stripGenericSeparator(tokens[i]);
            if (!isStructuredValueToken(value)) {
                continue;
            }
            for (int width = 1; width <= Math.min(3, i); width++) {
                int start = i - width;
                StringBuilder prefix = new StringBuilder();
                for (int j = start; j < i; j++) {
                    String token = stripGenericSeparator(tokens[j]);
                    if (token.isBlank() || isStructuredValueToken(token)) {
                        prefix.setLength(0);
                        break;
                    }
                    if (!prefix.isEmpty()) {
                        prefix.append(' ');
                    }
                    prefix.append(token);
                }
                if (!prefix.isEmpty()) {
                    labels.add(prefix + " " + value);
                }
            }
        }
        return List.copyOf(labels);
    }

    public static boolean isStructuredValueToken(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        String t = stripGenericSeparator(token);
        return t.matches("\\d{1,4}[a-z]?")
                || t.matches("[a-z]\\d{1,4}[a-z0-9]{0,6}")
                || t.matches("[a-z]{1,8}\\d[a-z0-9\\-]{1,12}");
    }

    private static String stripGenericSeparator(String token) {
        if (token == null) {
            return "";
        }
        return token.replaceAll("^[#:/\\-.]+|[#:/\\-.]+$", "").trim();
    }

    public static List<String> buildNgrams(String normalized, int minGram, int maxGram) {
        if (normalized.isBlank()) {
            return List.of();
        }
        String[] tokens = normalized.split("\\s+");
        if (tokens.length < minGram) {
            return List.of(normalized);
        }
        Set<String> ngrams = new LinkedHashSet<>();
        for (int n = minGram; n <= Math.min(maxGram, tokens.length); n++) {
            for (int i = 0; i <= tokens.length - n; i++) {
                StringBuilder sb = new StringBuilder();
                for (int j = 0; j < n; j++) {
                    if (j > 0) {
                        sb.append(' ');
                    }
                    sb.append(tokens[i + j]);
                }
                String gram = sb.toString().trim();
                if (gram.length() >= 4) {
                    ngrams.add(gram);
                }
            }
        }
        return List.copyOf(ngrams);
    }

    private static List<String> extractContentTokens(String normalized) {
        if (normalized.isBlank()) {
            return List.of();
        }
        List<String> tokens = new java.util.ArrayList<>();
        for (String t : normalized.split("\\s+")) {
            String term = t.trim();
            if (term.length() < 2) {
                continue;
            }
            tokens.add(term);
        }
        return tokens.stream().distinct().toList();
    }
}
