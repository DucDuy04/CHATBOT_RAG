package KLTN.RAG_CHATBOT_BE.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Generic response-layer cleanup for noisy composed table headers.
 */
final class TableHeaderDisplayCleaner {

    static final int MAX_DISPLAY_TOKENS = 8;
    static final int MAX_DISPLAY_CHARS = 64;

    private TableHeaderDisplayCleaner() {}

    static String cleanDisplayHeader(String rawHeader, int columnIndex) {
        if (rawHeader == null || rawHeader.isBlank()) {
            return fallback(columnIndex);
        }

        List<String> tokens = tokenize(rawHeader);
        if (tokens.isEmpty()) {
            return fallback(columnIndex);
        }

        tokens = removeAdjacentDuplicateTokens(tokens);
        tokens = removeRepeatedBlocks(tokens);
        tokens = removeRepeatedTokensAfterFirstPair(tokens);
        tokens = bound(tokens);

        String cleaned = String.join(" ", tokens).trim();
        return cleaned.isBlank() ? fallback(columnIndex) : cleaned;
    }

    static Map<String, String> cleanCellsForDisplay(Map<String, String> rawCells) {
        if (rawCells == null || rawCells.isEmpty()) {
            return Map.of();
        }

        Map<String, String> out = new LinkedHashMap<>();
        Set<String> used = new LinkedHashSet<>();
        int columnIndex = 0;
        for (Map.Entry<String, String> entry : rawCells.entrySet()) {
            String base = cleanDisplayHeader(entry.getKey(), columnIndex);
            String unique = uniqueKey(base, used, columnIndex);
            out.put(unique, entry.getValue());
            columnIndex++;
        }
        return out;
    }

    private static List<String> tokenize(String rawHeader) {
        String collapsed = rawHeader.replaceAll("\\s+", " ").trim();
        if (collapsed.isBlank()) {
            return List.of();
        }
        return new ArrayList<>(List.of(collapsed.split(" ")));
    }

    private static List<String> removeAdjacentDuplicateTokens(List<String> tokens) {
        List<String> out = new ArrayList<>();
        String previous = null;
        for (String token : tokens) {
            String normalized = normalize(token);
            if (!normalized.equals(previous)) {
                out.add(token);
            }
            previous = normalized;
        }
        return out;
    }

    private static List<String> removeRepeatedBlocks(List<String> tokens) {
        List<String> out = new ArrayList<>(tokens);
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int size = Math.min(4, out.size() / 2); size >= 2; size--) {
                for (int i = 0; i + (size * 2) <= out.size(); i++) {
                    if (sameBlock(out, i, i + size, size)) {
                        out.subList(i + size, i + (size * 2)).clear();
                        changed = true;
                        break;
                    }
                }
                if (changed) {
                    break;
                }
            }
        }
        return out;
    }

    private static List<String> removeRepeatedTokensAfterFirstPair(List<String> tokens) {
        if (tokens.size() < 4) {
            return tokens;
        }
        List<String> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String token : tokens) {
            String normalized = normalize(token);
            if (seen.add(normalized) || out.size() < 2) {
                out.add(token);
            }
        }
        return out;
    }

    private static List<String> bound(List<String> tokens) {
        List<String> out = new ArrayList<>();
        int chars = 0;
        for (String token : tokens) {
            int nextChars = chars + (out.isEmpty() ? 0 : 1) + token.length();
            if (!out.isEmpty() && (out.size() >= MAX_DISPLAY_TOKENS || nextChars > MAX_DISPLAY_CHARS)) {
                break;
            }
            out.add(token);
            chars = nextChars;
        }
        return out.isEmpty() ? tokens.subList(0, 1) : out;
    }

    private static boolean sameBlock(List<String> tokens, int left, int right, int size) {
        for (int offset = 0; offset < size; offset++) {
            if (!normalize(tokens.get(left + offset)).equals(normalize(tokens.get(right + offset)))) {
                return false;
            }
        }
        return true;
    }

    private static String uniqueKey(String base, Set<String> used, int columnIndex) {
        String candidate = base == null || base.isBlank() ? fallback(columnIndex) : base;
        if (used.add(candidate)) {
            return candidate;
        }
        int suffix = 2;
        while (!used.add(candidate + "_" + suffix)) {
            suffix++;
        }
        return candidate + "_" + suffix;
    }

    private static String fallback(int columnIndex) {
        return "col_" + Math.max(1, columnIndex + 1);
    }

    private static String normalize(String token) {
        return token == null ? "" : token.toLowerCase(Locale.ROOT);
    }
}
