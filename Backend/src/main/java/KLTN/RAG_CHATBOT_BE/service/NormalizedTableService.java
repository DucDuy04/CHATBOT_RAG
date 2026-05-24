package KLTN.RAG_CHATBOT_BE.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Generic normalized table row ingest (task 23J).
 */
@Service
@Slf4j
public class NormalizedTableService {

    private static final Pattern ROW_NUMBER_START = Pattern.compile("^\\s*\\d{1,4}\\s*([.|)]\\s*)?$");
    private static final Pattern GROUP_LABEL_PATTERN = Pattern.compile("^.+:\\s*.+$");
    private static final ObjectMapper JSON = new ObjectMapper();

    /** Max page gap before resetting logical table continuation (task 23J4). */
    static final int MAX_LOGICAL_TABLE_PAGE_GAP = 5;

    /** Max page span stored on a single normalized row batch. */
    static final int MAX_ROW_PAGE_SPAN = 3;

    public record NormalizationRequest(
            String tableMarkdown,
            String sectionTitle,
            String captionBeforeTable,
            int pageStart,
            int pageEnd,
            int tableIndexInSection,
            LogicalTableState continuationState
    ) {}

    public record NormalizationResult(
            boolean success,
            String tableName,
            List<NormalizedTableRow> rows,
            String tableSummaryContent,
            LogicalTableState updatedState,
            String failureReason,
            double confidence,
            QualityStats stats
    ) {
        static NormalizationResult fail(String reason) {
            return new NormalizationResult(false, null, List.of(), null, null, reason, 0.0, QualityStats.empty());
        }
    }

    public record QualityStats(
            int rowsWithCellsJson,
            int rowsWithOnlyOneNonEmptyCell,
            double rowsWithEmptyCellsRatio,
            int rowsWithGenericColumnKeys,
            int continuationRowsMerged,
            int multiRowHeadersMerged,
            int crossPageHeaderCarryCount,
            int sparseRowsRepaired,
            int droppedCellFragments
    ) {
        static QualityStats empty() {
            return new QualityStats(0, 0, 0.0, 0, 0, 0, 0, 0, 0);
        }
    }

    private record HeaderInference(
            List<String> headers,
            int dataStart,
            int multiRowHeadersMerged,
            int genericColumnKeys
    ) {}

    private record RowMergeResult(
            List<List<String>> rows,
            int continuationRowsMerged,
            int sparseRowsRepaired,
            int droppedCellFragments
    ) {}

    public NormalizationResult normalize(NormalizationRequest request) {
        if (request == null || request.tableMarkdown() == null || request.tableMarkdown().isBlank()) {
            return NormalizationResult.fail("empty-markdown");
        }

        ParsedTable parsed = parseMarkdownTable(request.tableMarkdown());
        if (parsed.rawRows().isEmpty()) {
            return NormalizationResult.fail("no-rows");
        }

        LogicalTableState state = resolveContinuationState(request);
        List<String> headers = null;
        int dataStart = 0;
        boolean inheritedHeaders = false;
        int multiRowHeadersMerged = 0;
        int genericColumnKeys = 0;
        int crossPageHeaderCarryCount = 0;
        String groupContext = state != null ? state.groupContext() : null;

        int scanFrom = 0;
        while (scanFrom < parsed.rawRows().size()) {
            List<String> scanRow = parsed.rawRows().get(scanFrom);
            if (isSeparatorRow(scanRow)) {
                scanFrom++;
                continue;
            }
            if (isGroupRow(scanRow, Math.max(parsed.maxColumns(), 2))) {
                groupContext = joinCells(scanRow);
                scanFrom++;
                continue;
            }
            break;
        }

        if (state != null && !parsed.rawRows().isEmpty()) {
            List<String> first = parsed.rawRows().get(0);
            if (!isSeparatorRow(first)
                    && !headersMatch(state.originalHeaders(), first)
                    && columnCountClose(state.columnCount(), Math.max(first.size(), parsed.maxColumns()))) {
                headers = state.originalHeaders();
                dataStart = 0;
                inheritedHeaders = true;
                crossPageHeaderCarryCount = 1;
            }
        }

        if (headers == null && scanFrom < parsed.rawRows().size()) {
            HeaderInference inferred = inferHeaders(parsed, scanFrom, state);
            if (state != null && inferred != null && headersMatch(state.originalHeaders(), inferred.headers())) {
                headers = state.originalHeaders();
                dataStart = inferred.dataStart();
                if (parsed.rawRows().size() > dataStart && isSeparatorRow(parsed.rawRows().get(dataStart))) {
                    dataStart++;
                }
            } else if (inferred != null) {
                headers = inferred.headers();
                dataStart = inferred.dataStart();
                multiRowHeadersMerged = inferred.multiRowHeadersMerged();
                genericColumnKeys = inferred.genericColumnKeys();
            }
        }

        if ((headers == null || headers.size() < 2) && parsed.maxColumns() >= 2) {
            headers = genericHeaders(parsed.maxColumns());
            dataStart = scanFrom;
            genericColumnKeys = headers.size();
        }

        if (headers == null || headers.size() < 2) {
            return NormalizationResult.fail("insufficient-headers");
        }

        String tableName = resolveTableName(request, state);
        int rowCounter = state != null ? state.rowIndexCounter() : 0;
        List<NormalizedTableRow> outRows = new ArrayList<>();
        List<String> headerKeys = headers.stream().map(NormalizedTableService::headerToKey).toList();

        List<List<String>> workingRows = new ArrayList<>(parsed.rawRows().subList(dataStart, parsed.rawRows().size()));
        RowMergeResult mergeResult = mergeWrappedRowsWithStats(workingRows, headers.size());
        workingRows = mergeResult.rows();

        int pageStart = request.pageStart() > 0 ? request.pageStart() : 1;
        int requestPageEnd = request.pageEnd() > 0 ? request.pageEnd() : pageStart;
        int pageEnd = Math.min(requestPageEnd, pageStart + MAX_ROW_PAGE_SPAN);

        for (List<String> raw : workingRows) {
            if (raw == null || raw.stream().allMatch(c -> c == null || c.isBlank())) {
                continue;
            }
            if (isSeparatorRow(raw)) {
                continue;
            }
            if (isGroupRow(raw, headers.size())) {
                groupContext = joinCells(raw);
                continue;
            }
            if (headersMatch(headers, raw)) {
                continue;
            }

            Map<String, String> cells = mapCells(headers, raw);
            if (!isDataRow(cells)) {
                continue;
            }

            rowCounter++;
            NormalizedTableRow row = NormalizedTableRow.of(
                    tableName, rowCounter, cells, pageStart, pageEnd, groupContext);
            outRows.add(row);
        }

        if (outRows.isEmpty()) {
            return NormalizationResult.fail("no-data-rows");
        }

        double confidence = inheritedHeaders ? 0.85 : 0.95;
        QualityStats stats = buildStats(
                outRows,
                genericColumnKeys,
                mergeResult.continuationRowsMerged(),
                multiRowHeadersMerged,
                crossPageHeaderCarryCount,
                mergeResult.sparseRowsRepaired(),
                mergeResult.droppedCellFragments());
        String summary = buildNeutralTableSummary(
                tableName, headers, outRows.size(), pageStart, pageEnd, groupContext);
        String logicalId = state != null ? state.logicalTableId()
                : "lt_" + request.tableIndexInSection() + "_" + pageStart;
        LogicalTableState newState = new LogicalTableState(
                logicalId,
                tableName,
                headers,
                headerKeys,
                headers.size(),
                pageEnd,
                request.sectionTitle(),
                groupContext,
                rowCounter);

        return new NormalizationResult(
                true, tableName, outRows, summary, newState, null, confidence, stats);
    }

    public static String buildCanonicalText(
            String tableName,
            int rowIndex,
            Map<String, String> cells,
            String groupContext,
            int pageStart
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("Bảng: ").append(safe(tableName)).append(".\n");
        if (groupContext != null && !groupContext.isBlank()) {
            sb.append("Context: ").append(groupContext.trim()).append(".\n");
        }
        sb.append("Dòng: ").append(rowIndex).append(".\n");
        for (Map.Entry<String, String> e : cells.entrySet()) {
            if (e.getValue() == null || e.getValue().isBlank()) {
                continue;
            }
            sb.append(e.getKey()).append(": ").append(e.getValue().trim()).append(".\n");
        }
        sb.append("Trang: ").append(pageStart).append(".");
        return sb.toString().trim();
    }

    public static String buildNeutralTableSummary(
            String tableName,
            List<String> headers,
            int rowCount,
            int pageStart,
            int pageEnd,
            String groupContext
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("Bảng: ").append(safe(tableName)).append(".\n");
        if (pageStart == pageEnd) {
            sb.append("Trang: ").append(pageStart).append(".\n");
        } else {
            sb.append("Trang: ").append(pageStart).append("-").append(pageEnd).append(".\n");
        }
        if (groupContext != null && !groupContext.isBlank()) {
            sb.append("Context: ").append(groupContext.trim()).append(".\n");
        }
        sb.append("Các cột: ").append(String.join(", ", headers)).append(".\n");
        sb.append("Số dòng đã chuẩn hóa: ").append(rowCount).append(".\n");
        sb.append("Bảng chứa các dòng dữ liệu theo các cột trên.");
        return sb.toString();
    }

    public static String cellsToJson(Map<String, String> cells) {
        try {
            return JSON.writeValueAsString(cells);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    /** Profile built from normalized table cells/headers for raw text suppression (task 23J3). */
    public record SuppressionProfile(
            Set<String> cellTokens,
            Set<String> headerTokens
    ) {
        public static SuppressionProfile empty() {
            return new SuppressionProfile(Set.of(), Set.of());
        }

        public SuppressionProfile merge(SuppressionProfile other) {
            if (other == null) {
                return this;
            }
            Set<String> cells = new LinkedHashSet<>(cellTokens);
            cells.addAll(other.cellTokens);
            Set<String> headers = new LinkedHashSet<>(headerTokens);
            headers.addAll(other.headerTokens);
            return new SuppressionProfile(Set.copyOf(cells), Set.copyOf(headers));
        }
    }

    public record SuppressResult(
            String text,
            long suppressedRawChars,
            int suppressedLines,
            int tableLikeLinesDropped
    ) {}

    public SuppressionProfile buildSuppressionProfile(String tableMarkdown) {
        if (tableMarkdown == null || tableMarkdown.isBlank()) {
            return SuppressionProfile.empty();
        }
        ParsedTable parsed = parseMarkdownTable(tableMarkdown);
        Set<String> cells = new LinkedHashSet<>();
        Set<String> headers = new LinkedHashSet<>();
        List<String> headerLabels = extractHeaderLabels(parsed);
        for (String h : headerLabels) {
            addTokensFromCell(h, headers, 3);
        }
        for (List<String> row : parsed.rawRows()) {
            for (String cell : row) {
                addTokensFromCell(cell, cells, 3);
            }
        }
        return new SuppressionProfile(Set.copyOf(cells), Set.copyOf(headers));
    }

    public SuppressionProfile buildSuppressionProfile(NormalizationResult result) {
        if (result == null || !result.success() || result.rows().isEmpty()) {
            return SuppressionProfile.empty();
        }
        Set<String> cells = new LinkedHashSet<>();
        Set<String> headers = new LinkedHashSet<>();
        for (NormalizedTableRow row : result.rows()) {
            if (row.cells() != null) {
                for (Map.Entry<String, String> e : row.cells().entrySet()) {
                    addTokensFromCell(e.getKey(), headers, 3);
                    addTokensFromCell(e.getValue(), cells, 3);
                }
            }
        }
        return new SuppressionProfile(Set.copyOf(cells), Set.copyOf(headers));
    }

    /**
     * Remove lines from plain text that overlap extracted table cell text (raw suppression).
     */
    public String suppressTableOverlappingText(String pageText, String tableMarkdown) {
        SuppressionProfile profile = buildSuppressionProfile(tableMarkdown);
        return suppressRawTableText(pageText, profile).text();
    }

    public SuppressResult suppressRawTableText(String pageText, SuppressionProfile profile) {
        if (pageText == null || pageText.isBlank()) {
            return new SuppressResult(pageText == null ? "" : pageText, 0, 0, 0);
        }
        if (profile == null || (profile.cellTokens().isEmpty() && profile.headerTokens().isEmpty())) {
            return new SuppressResult(pageText, 0, 0, 0);
        }

        StringBuilder kept = new StringBuilder();
        long removedChars = 0;
        int suppressedLines = 0;
        int tableLikeDropped = 0;
        for (String line : pageText.split("\\R", -1)) {
            if (shouldSuppressRawLine(line, profile)) {
                removedChars += line.length();
                suppressedLines++;
                if (looksLikeTableDataLine(line)) {
                    tableLikeDropped++;
                }
                continue;
            }
            kept.append(line).append("\n");
        }
        return new SuppressResult(kept.toString().trim(), removedChars, suppressedLines, tableLikeDropped);
    }

    /** Post-chunk safety net: drop text that still looks like table rows overlapping normalized cells. */
    public boolean shouldDropLeakyTextChunk(String chunkText, SuppressionProfile profile) {
        if (chunkText == null || chunkText.isBlank() || profile == null) {
            return false;
        }
        if (!hasHighTableLikeDensity(chunkText)) {
            return false;
        }
        int overlapLines = 0;
        int tableLikeLines = 0;
        for (String line : chunkText.split("\\R", -1)) {
            if (line.isBlank()) {
                continue;
            }
            if (looksLikeTableDataLine(line)) {
                tableLikeLines++;
            }
            if (shouldSuppressRawLine(line, profile)) {
                overlapLines++;
            }
        }
        return tableLikeLines >= 3 && overlapLines >= Math.max(2, tableLikeLines / 2);
    }

    static boolean shouldSuppressRawLine(String line, SuppressionProfile profile) {
        if (line == null || line.isBlank() || profile == null) {
            return false;
        }
        String normLine = normalizeForMatch(line);
        if (normLine.length() < 3) {
            return false;
        }
        int headerMatches = countTokenMatches(normLine, profile.headerTokens(), 3);
        if (headerMatches >= 2) {
            return true;
        }
        int cellMatches = countTokenMatches(normLine, profile.cellTokens(), 3);
        if (cellMatches == 0) {
            return false;
        }
        if (cellMatches >= 2) {
            return true;
        }
        return looksLikeTableDataLine(line);
    }

    static boolean looksLikeTableDataLine(String line) {
        if (line == null || line.isBlank()) {
            return false;
        }
        String t = line.trim();
        if (t.startsWith("|")) {
            return true;
        }
        if (t.contains("\t") && t.split("\t").length >= 2) {
            return true;
        }
        if (t.split("\\s{2,}").length >= 2) {
            return true;
        }
        return isSingleSpaceTableLikeLine(t);
    }

    static boolean isSingleSpaceTableLikeLine(String trimmed) {
        String[] tokens = trimmed.split("\\s+");
        if (tokens.length < 4) {
            return false;
        }
        if (tokens[0].matches("\\d{1,4}") && tokens.length >= 5) {
            return true;
        }
        int codes = 0;
        int dates = 0;
        int nums = 0;
        int shortTok = 0;
        for (String tok : tokens) {
            if (tok.matches("[A-Za-z]{2,}\\d{2,}[A-Za-z0-9\\-]*")) {
                codes++;
            } else if (tok.matches("\\d{1,2}/\\d{1,2}/\\d{2,4}")) {
                dates++;
            } else if (tok.matches("\\d{1,4}")) {
                nums++;
            }
            if (tok.length() <= 6) {
                shortTok++;
            }
        }
        if (codes >= 1 && (dates >= 1 || nums >= 2)) {
            return true;
        }
        if (dates >= 1 && nums >= 2) {
            return true;
        }
        return shortTok >= tokens.length * 0.55 && tokens.length >= 6;
    }

    static boolean hasHighTableLikeDensity(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        int nonBlank = 0;
        int tableLike = 0;
        for (String line : text.split("\\R", -1)) {
            if (line.isBlank()) {
                continue;
            }
            nonBlank++;
            if (looksLikeTableDataLine(line)) {
                tableLike++;
            }
        }
        return nonBlank >= 3 && tableLike >= nonBlank * 0.30;
    }

    private static int countTokenMatches(String normLine, Set<String> tokens, int minLen) {
        if (tokens == null || tokens.isEmpty()) {
            return 0;
        }
        int matches = 0;
        for (String token : tokens) {
            if (token.length() >= minLen && normLine.contains(token)) {
                matches++;
            }
        }
        return matches;
    }

    private static void addTokensFromCell(String cell, Set<String> target, int minLen) {
        if (cell == null || cell.isBlank()) {
            return;
        }
        String norm = normalizeForMatch(cell);
        if (norm.length() >= minLen) {
            target.add(norm);
        }
        for (String part : norm.split("\\s+")) {
            if (part.length() >= minLen) {
                target.add(part);
            }
        }
    }

    private static List<String> extractHeaderLabels(ParsedTable parsed) {
        if (parsed == null || parsed.rawRows().isEmpty()) {
            return List.of();
        }
        int idx = parsed.separatorIndex() > 0 ? parsed.separatorIndex() - 1 : 0;
        if (idx >= parsed.rawRows().size()) {
            idx = 0;
        }
        List<String> row = parsed.rawRows().get(idx);
        if (isSeparatorRow(row)) {
            return List.of();
        }
        return row.stream().filter(c -> c != null && !c.isBlank()).map(String::trim).toList();
    }

    LogicalTableState resolveContinuationState(NormalizationRequest request) {
        LogicalTableState state = request.continuationState();
        if (state == null) {
            return null;
        }
        int pageStart = request.pageStart() > 0 ? request.pageStart() : 1;
        if (state.lastPage() > 0 && pageStart - state.lastPage() > MAX_LOGICAL_TABLE_PAGE_GAP) {
            log.debug("[NormalizedTable] Reset logical table: page gap {} (last={} new={})",
                    pageStart - state.lastPage(), state.lastPage(), pageStart);
            return null;
        }
        if (request.captionBeforeTable() != null && !request.captionBeforeTable().isBlank()
                && state.tableName() != null
                && !request.captionBeforeTable().trim().equalsIgnoreCase(state.tableName().trim())) {
            log.debug("[NormalizedTable] Reset logical table: new caption '{}'", request.captionBeforeTable());
            return null;
        }
        int span = request.pageEnd() - request.pageStart();
        if (span > 30) {
            log.debug("[NormalizedTable] Wide page span {}-{} — not carrying tableName across section",
                    request.pageStart(), request.pageEnd());
            return null;
        }
        return state;
    }

    private String resolveTableName(NormalizationRequest request, LogicalTableState state) {
        if (request.captionBeforeTable() != null && !request.captionBeforeTable().isBlank()) {
            return request.captionBeforeTable().trim();
        }
        if (state != null && state.tableName() != null && !state.tableName().isBlank()) {
            return state.tableName();
        }
        if (request.sectionTitle() != null && !request.sectionTitle().isBlank()) {
            return request.sectionTitle().trim();
        }
        int p = request.pageStart() > 0 ? request.pageStart() : 1;
        return "Table page " + p + " #" + (request.tableIndexInSection() + 1);
    }

    private static HeaderInference inferHeaders(ParsedTable parsed, int scanFrom, LogicalTableState state) {
        if (parsed == null || parsed.rawRows().isEmpty()) {
            return null;
        }
        List<List<String>> rows = parsed.rawRows();
        int start = scanFrom;
        while (start < rows.size()
                && (isSeparatorRow(rows.get(start)) || countNonEmpty(rows.get(start)) == 0)) {
            start++;
        }
        if (start >= rows.size()) {
            return null;
        }
        int maxCols = Math.max(parsed.maxColumns(), rows.get(start).size());
        if (state != null && headersMatch(state.originalHeaders(), rows.get(start))) {
            int dataStart = start + 1;
            while (dataStart < rows.size() && isSeparatorRow(rows.get(dataStart))) {
                dataStart++;
            }
            return new HeaderInference(state.originalHeaders(), dataStart, 0, 0);
        }
        if (parsed.separatorIndex() > start) {
            List<Integer> explicitHeaderRows = new ArrayList<>();
            for (int i = start; i < parsed.separatorIndex(); i++) {
                if (countNonEmpty(rows.get(i)) > 0 && !isSeparatorRow(rows.get(i))) {
                    explicitHeaderRows.add(i);
                }
            }
            if (!explicitHeaderRows.isEmpty()) {
                List<String> headers = composeHeaders(rows, explicitHeaderRows, maxCols);
                int generic = (int) headers.stream().filter(NormalizedTableService::isGenericHeader).count();
                int dataStart = parsed.separatorIndex() + 1;
                while (dataStart < rows.size()
                        && (isSeparatorRow(rows.get(dataStart)) || countNonEmpty(rows.get(dataStart)) == 0)) {
                    dataStart++;
                }
                return new HeaderInference(headers, dataStart, Math.max(0, explicitHeaderRows.size() - 1), generic);
            }
        }

        List<Integer> headerIndexes = new ArrayList<>();
        int limit = Math.min(rows.size(), start + 6);
        for (int i = start; i < limit; i++) {
            List<String> row = rows.get(i);
            if (isSeparatorRow(row) || countNonEmpty(row) == 0) {
                continue;
            }
            if (isStrongDataStart(row, maxCols)) {
                if (!headerIndexes.isEmpty()) {
                    break;
                }
                return null;
            }
            if (looksLikeHeaderRow(row, rows, i) || isSparseHeaderFragment(row, maxCols)) {
                headerIndexes.add(i);
                continue;
            }
            if (headerIndexes.isEmpty() && countNonEmpty(row) >= 2) {
                headerIndexes.add(i);
            }
            break;
        }

        if (headerIndexes.isEmpty()) {
            return null;
        }

        List<String> headers = composeHeaders(rows, headerIndexes, maxCols);
        int dataStart = headerIndexes.get(headerIndexes.size() - 1) + 1;
        while (dataStart < rows.size()
                && (isSeparatorRow(rows.get(dataStart)) || countNonEmpty(rows.get(dataStart)) == 0)) {
            dataStart++;
        }
        int generic = (int) headers.stream().filter(NormalizedTableService::isGenericHeader).count();
        return new HeaderInference(headers, dataStart, Math.max(0, headerIndexes.size() - 1), generic);
    }

    private static List<String> composeHeaders(List<List<String>> rows, List<Integer> headerIndexes, int maxCols) {
        List<String> headers = new ArrayList<>();
        for (int col = 0; col < maxCols; col++) {
            LinkedHashSet<String> parts = new LinkedHashSet<>();
            for (Integer idx : headerIndexes) {
                List<String> row = rows.get(idx);
                String val = col < row.size() ? safe(row.get(col)).replaceAll("\\s+", " ") : "";
                if (!val.isBlank() && !isSeparatorText(val)) {
                    parts.add(val);
                }
            }
            String composed = String.join(" ", parts).trim().replaceAll("\\s+", " ");
            headers.add(composed.isBlank() ? "col_" + (col + 1) : composed);
        }
        return disambiguateHeaders(headers);
    }

    private static List<String> disambiguateHeaders(List<String> headers) {
        Map<String, Integer> seen = new HashMap<>();
        List<String> out = new ArrayList<>();
        for (int i = 0; i < headers.size(); i++) {
            String h = safe(headers.get(i));
            if (h.isBlank()) {
                h = "col_" + (i + 1);
            }
            String key = normalizeForMatch(h);
            int count = seen.getOrDefault(key, 0) + 1;
            seen.put(key, count);
            out.add(count == 1 ? h : h + "_" + count);
        }
        return out;
    }

    private static List<String> genericHeaders(int count) {
        List<String> headers = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            headers.add("col_" + (i + 1));
        }
        return headers;
    }

    private static boolean isGenericHeader(String header) {
        return header != null && header.matches("col_\\d+");
    }

    private static boolean isSparseHeaderFragment(List<String> row, int expectedCols) {
        int nonEmpty = countNonEmpty(row);
        if (nonEmpty < 1 || expectedCols < 3) {
            return false;
        }
        if (isStrongDataStart(row, expectedCols)) {
            return false;
        }
        int numeric = countNumericOnly(row);
        return nonEmpty <= Math.max(4, expectedCols / 2) && numeric <= nonEmpty / 2;
    }

    private static boolean isStrongDataStart(List<String> row, int expectedCols) {
        if (row == null || row.isEmpty()) {
            return false;
        }
        String first = firstNonEmpty(row);
        if (first.isBlank()) {
            return false;
        }
        if (ROW_NUMBER_START.matcher(first).matches()) {
            return true;
        }
        int nonEmpty = countNonEmpty(row);
        int numeric = countNumericOnly(row);
        return expectedCols >= 3 && nonEmpty >= 2 && numeric >= Math.max(2, nonEmpty / 2);
    }

    private static int countNonEmpty(List<String> row) {
        if (row == null) {
            return 0;
        }
        int count = 0;
        for (String cell : row) {
            if (cell != null && !cell.isBlank()) {
                count++;
            }
        }
        return count;
    }

    private static int countNumericOnly(List<String> row) {
        if (row == null) {
            return 0;
        }
        int count = 0;
        for (String cell : row) {
            String text = safe(cell);
            if (!text.isBlank() && text.matches("^\\d+([.,]\\d+)?$")) {
                count++;
            }
        }
        return count;
    }

    private static String firstNonEmpty(List<String> row) {
        if (row == null) {
            return "";
        }
        for (String cell : row) {
            String text = safe(cell);
            if (!text.isBlank()) {
                return text;
            }
        }
        return "";
    }

    private static boolean isSeparatorText(String value) {
        return value == null || value.replaceAll("[|\\-\\s:]", "").isBlank();
    }

    private static QualityStats buildStats(
            List<NormalizedTableRow> rows,
            int genericColumnKeys,
            int continuationRowsMerged,
            int multiRowHeadersMerged,
            int crossPageHeaderCarryCount,
            int sparseRowsRepaired,
            int droppedCellFragments
    ) {
        if (rows == null || rows.isEmpty()) {
            return QualityStats.empty();
        }
        int onlyOne = 0;
        int totalCells = 0;
        int emptyCells = 0;
        for (NormalizedTableRow row : rows) {
            int nonEmpty = 0;
            for (String value : row.cells().values()) {
                totalCells++;
                if (value == null || value.isBlank()) {
                    emptyCells++;
                } else {
                    nonEmpty++;
                }
            }
            if (nonEmpty == 1) {
                onlyOne++;
            }
        }
        double emptyRatio = totalCells == 0 ? 0.0 : (double) emptyCells / totalCells;
        return new QualityStats(
                rows.size(), onlyOne, emptyRatio, genericColumnKeys,
                continuationRowsMerged, multiRowHeadersMerged, crossPageHeaderCarryCount,
                sparseRowsRepaired, droppedCellFragments);
    }

    private static Map<String, String> mapCells(List<String> headers, List<String> raw) {
        Map<String, String> cells = new LinkedHashMap<>();
        List<String> source = raw == null ? List.of() : raw;
        int cols = Math.max(headers.size(), source.size());
        for (int i = 0; i < cols; i++) {
            String header = i < headers.size() ? headers.get(i) : "col_" + (i + 1);
            String val = i < source.size() ? safe(source.get(i)) : "";
            cells.put(header, val);
        }
        return cells;
    }

    private static boolean isDataRow(Map<String, String> cells) {
        if (cells == null || cells.isEmpty()) {
            return false;
        }
        return cells.values().stream().anyMatch(v -> v != null && !v.isBlank());
    }

    private static boolean isGroupRow(List<String> cells, int expectedCols) {
        if (cells == null || cells.isEmpty()) {
            return false;
        }
        long nonEmpty = cells.stream().filter(c -> c != null && !c.isBlank()).count();
        if (nonEmpty == 0) {
            return false;
        }
        if (nonEmpty == 1) {
            String only = cells.stream().filter(c -> c != null && !c.isBlank()).findFirst().orElse("");
            return GROUP_LABEL_PATTERN.matcher(only.trim()).matches() || only.length() > 20;
        }
        String joined = joinCells(cells);
        if (GROUP_LABEL_PATTERN.matcher(joined).matches() && nonEmpty < expectedCols) {
            return true;
        }
        return cells.size() < expectedCols / 2 && joined.contains(":");
    }

    private static String joinCells(List<String> cells) {
        return cells.stream()
                .filter(c -> c != null && !c.isBlank())
                .map(String::trim)
                .reduce((a, b) -> a + " " + b)
                .orElse("");
    }

    static List<List<String>> mergeWrappedRows(List<List<String>> rows, int expectedCols) {
        return mergeWrappedRowsWithStats(rows, expectedCols).rows();
    }

    private static RowMergeResult mergeWrappedRowsWithStats(List<List<String>> rows, int expectedCols) {
        if (rows == null || rows.size() < 2) {
            return new RowMergeResult(rows == null ? List.of() : rows, 0, 0, 0);
        }
        List<List<String>> merged = new ArrayList<>();
        int continuationMerged = 0;
        int sparseRepaired = 0;
        int droppedFragments = 0;
        for (List<String> row : rows) {
            if (row == null) {
                continue;
            }
            if (merged.isEmpty()) {
                merged.add(new ArrayList<>(row));
                continue;
            }
            if (isContinuationRow(row, expectedCols, merged.get(merged.size() - 1))) {
                List<String> prev = merged.get(merged.size() - 1);
                int beforeEmpty = emptyCellCount(prev);
                int appended = mergeIntoPrevious(prev, row);
                int afterEmpty = emptyCellCount(prev);
                continuationMerged++;
                if (afterEmpty < beforeEmpty || appended > 0) {
                    sparseRepaired++;
                }
            } else {
                merged.add(new ArrayList<>(row));
            }
        }
        return new RowMergeResult(merged, continuationMerged, sparseRepaired, droppedFragments);
    }

    private static boolean isContinuationRow(List<String> row, int expectedCols, List<String> previous) {
        if (row == null || previous == null) {
            return false;
        }
        String first = row.isEmpty() ? "" : safe(row.get(0));
        if (!first.isBlank() && ROW_NUMBER_START.matcher(first).matches()) {
            return false;
        }
        if (!first.isBlank() && !looksLikeWrappedFragment(first)) {
            return false;
        }
        long nonEmpty = row.stream().filter(c -> c != null && !c.isBlank()).count();
        if (nonEmpty == 0) {
            return false;
        }
        if (first.isBlank() && previous.stream().anyMatch(c -> c != null && !c.isBlank()) && nonEmpty > 0) {
            return true;
        }
        if (row.size() < expectedCols && first.isBlank()) {
            return true;
        }
        if (nonEmpty <= 2 && row.size() < expectedCols) {
            String prevFirst = previous.isEmpty() ? "" : safe(previous.get(0));
            return prevFirst.isBlank() || !ROW_NUMBER_START.matcher(prevFirst).matches();
        }
        if (nonEmpty > 0 && row.size() <= expectedCols) {
            String prevFirst = previous.isEmpty() ? "" : safe(previous.get(0));
            if (!prevFirst.isBlank() && looksLikeWrappedFragment(first) && first.length() < 40) {
                return true;
            }
        }
        return false;
    }

    private static boolean looksLikeWrappedFragment(String first) {
        if (first == null || first.isBlank()) {
            return true;
        }
        if (first.length() <= 4 && first.matches("^[A-Za-z0-9][A-Za-z0-9._-]*$")) {
            return false;
        }
        return Character.isLowerCase(first.charAt(0));
    }

    private static int mergeIntoPrevious(List<String> prev, List<String> cont) {
        int appended = 0;
        for (int i = 0; i < cont.size(); i++) {
            String add = safe(cont.get(i));
            if (add.isBlank()) {
                continue;
            }
            if (i < prev.size()) {
                String existing = safe(prev.get(i));
                prev.set(i, existing.isBlank() ? add : existing + " " + add);
            } else {
                prev.add(add);
            }
            appended++;
        }
        return appended;
    }

    private static int emptyCellCount(List<String> cells) {
        if (cells == null) {
            return 0;
        }
        int count = 0;
        for (String cell : cells) {
            if (cell == null || cell.isBlank()) {
                count++;
            }
        }
        return count;
    }

    private static boolean looksLikeHeaderRow(List<String> row, List<List<String>> all, int index) {
        if (row == null || row.stream().filter(c -> c != null && !c.isBlank()).count() < 2) {
            return false;
        }
        int textCells = 0;
        int numericOnly = 0;
        for (String c : row) {
            if (c == null || c.isBlank()) {
                continue;
            }
            textCells++;
            if (c.trim().matches("^\\d+([.,]\\d+)?$")) {
                numericOnly++;
            }
        }
        if (textCells > 0 && numericOnly >= textCells * 0.8) {
            return false;
        }
        if (index == 0) {
            return true;
        }
        return index <= 2;
    }

    private static List<String> normalizeHeaderLabels(List<String> row) {
        List<String> out = new ArrayList<>();
        for (String c : row) {
            out.add(c == null ? "" : c.trim().replaceAll("\\s+", " "));
        }
        while (!out.isEmpty() && out.get(out.size() - 1).isBlank()) {
            out.remove(out.size() - 1);
        }
        while (!out.isEmpty() && out.get(0).isBlank()) {
            out.remove(0);
        }
        return out;
    }

    private static boolean headersMatch(List<String> a, List<String> b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) {
            return false;
        }
        int n = Math.min(a.size(), b.size());
        int matches = 0;
        for (int i = 0; i < n; i++) {
            if (normalizeForMatch(a.get(i)).equals(normalizeForMatch(b.get(i)))) {
                matches++;
            }
        }
        return matches >= Math.max(2, n * 2 / 3);
    }

    private static boolean columnCountClose(int expected, int actual) {
        if (expected <= 0 || actual <= 0) {
            return false;
        }
        return Math.abs(expected - actual) <= 1;
    }

    static String headerToKey(String header) {
        if (header == null || header.isBlank()) {
            return "col";
        }
        String n = Normalizer.normalize(header, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replace('đ', 'd')
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        return n.isBlank() ? "col" : n;
    }

    private static String normalizeForMatch(String s) {
        if (s == null) {
            return "";
        }
        return Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static boolean isSeparatorRow(List<String> cells) {
        if (cells == null || cells.isEmpty()) {
            return false;
        }
        return cells.stream().allMatch(c ->
                c == null || c.isBlank() || c.replaceAll("[|\\-\\s:]", "").isBlank());
    }

    private record ParsedTable(
            List<List<String>> rawRows,
            int separatorIndex,
            int maxColumns,
            boolean hasExplicitHeader
    ) {}

    static ParsedTable parseMarkdownTable(String markdown) {
        List<List<String>> rows = new ArrayList<>();
        int sepIdx = -1;
        int maxCols = 0;
        for (String line : markdown.split("\\R")) {
            String trimmed = line.trim();
            if (!trimmed.startsWith("|")) {
                continue;
            }
            List<String> cells = parseMarkdownRow(trimmed);
            if (cells.isEmpty()) {
                continue;
            }
            maxCols = Math.max(maxCols, cells.size());
            if (sepIdx < 0 && isSeparatorRow(cells)) {
                sepIdx = rows.size();
            }
            rows.add(cells);
        }
        boolean hasHeader = sepIdx > 0 || (!rows.isEmpty() && looksLikeHeaderRow(rows.get(0), rows, 0));
        return new ParsedTable(rows, sepIdx, maxCols, hasHeader);
    }

    static List<String> parseMarkdownRow(String line) {
        if (line == null || !line.trim().startsWith("|")) {
            return List.of();
        }
        String[] parts = line.split("\\|", -1);
        List<String> cells = new ArrayList<>();
        for (int i = 0; i < parts.length; i++) {
            if (i == 0 && parts[i].isBlank()) {
                continue;
            }
            if (i == parts.length - 1 && parts[i].isBlank()) {
                continue;
            }
            cells.add(parts[i].trim().replaceAll("\\s+", " "));
        }
        return cells;
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }
}
