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

    /**
     * A coordinate header cell is "broad spanning" when its width exceeds this multiple of the target
     * slot width. Broad cells are demoted from leaf-column headers to parent/context role.
     */
    private static final double BROAD_HEADER_CELL_FACTOR = 2.5;

    /**
     * When {@code >= BROAD_HEADER_REUSE_MIN} consecutive slots end up with the same non-generic
     * pre-disambiguation header text, the run is treated as a broad-spanning parent header and all
     * slots in the run are replaced with {@code col_N} fallbacks.
     */
    private static final int BROAD_HEADER_REUSE_MIN = 3;

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
            int droppedCellFragments,
            int headerSlotsCreated,
            int headerSlotsFallbackGeneric,
            int headerSiblingContaminationPrevented,
            int headerAmbiguousFallbackCount,
            double avgHeaderTokenCountBefore,
            double avgHeaderTokenCountAfter,
            int noisyComposedHeaderBeforeCount,
            int noisyComposedHeaderAfterCount,
            int compactHeaderSuspiciousCount,
            int compactHeaderFallbackCount,
            int spanAwareHeaderSelectedCount,
            int multiColumnHeaderRejectedCount,
            int headerFragmentsWithCoordinates,
            int headerFragmentsWithoutCoordinates,
            int valuesPreservedCount,
            int valuesDroppedCount,
            int zeroOverlapHeaderRejected,
            int broadSpanningHeaderDemoted,
            int collisionSuffixPrevented
    ) {
        static QualityStats empty() {
            return new QualityStats(0, 0, 0.0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0.0, 0.0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        }
    }

    private record HeaderInference(
            List<String> headers,
            HeaderAttribution attribution,
            int dataStart,
            int multiRowHeadersMerged,
            int genericColumnKeys
    ) {}

    static record HeaderSlot(
            int columnIndex,
            String selectedHeader,
            List<String> rawHeaderFragments,
            double confidence,
            boolean fallbackGeneric,
            int selectedSourceFragmentCount,
            boolean multiColumnRejected
    ) {}

    private record HeaderAttribution(
            List<HeaderSlot> slots,
            int siblingContaminationPrevented,
            int ambiguousFallbackCount,
            int compactSuspiciousCount,
            int compactFallbackCount,
            int spanAwareSelectedCount,
            int multiColumnRejectedCount,
            int fragmentsWithCoordinates,
            int fragmentsWithoutCoordinates,
            double avgTokenCountBefore,
            double avgTokenCountAfter,
            int noisyBeforeCount,
            int noisyAfterCount,
            List<String> contextualFragments
    ) {
        static HeaderAttribution empty() {
            return new HeaderAttribution(List.of(), 0, 0, 0, 0, 0, 0, 0, 0,
                    0.0, 0.0, 0, 0, List.of());
        }
    }

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
        HeaderAttribution headerAttribution = HeaderAttribution.empty();
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
                headerAttribution = attributionFromHeaders(headers);
            }
        }

        if (headers == null && scanFrom < parsed.rawRows().size()) {
            HeaderInference inferred = inferHeaders(parsed, scanFrom, state);
            if (state != null && inferred != null && headersMatch(state.originalHeaders(), inferred.headers())) {
                headers = state.originalHeaders();
                dataStart = inferred.dataStart();
                headerAttribution = attributionFromHeaders(headers);
                if (parsed.rawRows().size() > dataStart && isSeparatorRow(parsed.rawRows().get(dataStart))) {
                    dataStart++;
                }
            } else if (inferred != null) {
                headers = inferred.headers();
                dataStart = inferred.dataStart();
                multiRowHeadersMerged = inferred.multiRowHeadersMerged();
                genericColumnKeys = inferred.genericColumnKeys();
                headerAttribution = inferred.attribution();
            }
        }

        if ((headers == null || headers.size() < 2) && parsed.maxColumns() >= 2) {
            headers = genericHeaders(parsed.maxColumns());
            dataStart = scanFrom;
            genericColumnKeys = headers.size();
            headerAttribution = attributionFromHeaders(headers);
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
        String headerContext = joinHeaderContext(headerAttribution.contextualFragments());

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
            String rowGroupContext = effectiveGroupContext(groupContext, headerContext);
            NormalizedTableRow row = NormalizedTableRow.of(
                    tableName, rowCounter, cells, pageStart, pageEnd, rowGroupContext);
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
                mergeResult.droppedCellFragments(),
                headerAttribution,
                0, 0, 0);
        String summary = buildNeutralTableSummary(
                tableName, headers, outRows.size(), pageStart, pageEnd,
                effectiveGroupContext(groupContext, headerContext));
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
                effectiveGroupContext(groupContext, headerContext),
                rowCounter);

        return new NormalizationResult(
                true, tableName, outRows, summary, newState, null, confidence, stats);
    }

    public NormalizationResult normalizeRawTable(RawTableModel table, NormalizationRequest request) {
        if (table == null || table.rows() == null || table.rows().isEmpty()) {
            return NormalizationResult.fail("empty-raw-table");
        }
        int maxCols = table.rows().stream().mapToInt(r -> r.cells() == null ? 0 : r.cells().size()).max().orElse(0);
        if (maxCols < 2) {
            return NormalizationResult.fail("insufficient-raw-columns");
        }

        int scanFrom = firstSubstantiveRawRow(table.rows());
        int dataStart = inferRawDataStart(table.rows(), scanFrom, maxCols);
        if (dataStart <= scanFrom && dataStart < table.rows().size() && !isStrongRawDataRow(table.rows().get(dataStart), maxCols)) {
            dataStart = Math.min(table.rows().size(), scanFrom + 1);
        }
        CoordInferenceResult inferResult = inferHeadersFromCoordinates(table, scanFrom, dataStart, maxCols);
        List<CoordinateHeaderSlot> slots = inferResult.slots();
        if (slots.size() < 2) {
            return NormalizationResult.fail("insufficient-coordinate-headers");
        }

        CoordinateMergeResult mergeResult = mergeWrappedRowsWithCoordinateGuard(
                table.rows().subList(Math.min(dataStart, table.rows().size()), table.rows().size()), slots);
        String tableName = resolveTableName(request, null);
        int requestPageStart = request == null ? 1 : request.pageStart();
        int pageStart = table.pageNumber() > 0 ? table.pageNumber() : requestPageStart;
        if (pageStart <= 0) {
            pageStart = 1;
        }
        int pageEnd = pageStart;
        List<String> headers = slots.stream().map(CoordinateHeaderSlot::header).toList();
        List<NormalizedTableRow> outRows = new ArrayList<>();
        int rowCounter = 0;
        for (CoordinateLogicalRow row : mergeResult.rows()) {
            Map<String, String> cells = mapCellsFromCoordinates(row, slots);
            if (!isDataRow(cells)) {
                continue;
            }
            rowCounter++;
            outRows.add(NormalizedTableRow.of(tableName, rowCounter, cells, pageStart, pageEnd, null));
        }
        if (outRows.isEmpty()) {
            return NormalizationResult.fail("no-coordinate-data-rows");
        }

        HeaderAttribution attribution = coordinateAttribution(slots);
        QualityStats stats = buildStats(
                outRows,
                (int) slots.stream().filter(CoordinateHeaderSlot::fallbackGeneric).count(),
                mergeResult.acceptedMerges(),
                Math.max(0, dataStart - scanFrom - 1),
                0,
                mergeResult.acceptedMerges(),
                0,
                attribution,
                inferResult.zeroOverlapRejected(),
                inferResult.broadSpanningDemoted(),
                inferResult.collisionSuffixPrevented());
        String summary = buildNeutralTableSummary(tableName, headers, outRows.size(), pageStart, pageEnd, null);
        LogicalTableState state = new LogicalTableState(
                "lt_raw_" + table.pageNumber() + "_" + table.tableIndexOnPage(),
                tableName,
                headers,
                headers.stream().map(NormalizedTableService::headerToKey).toList(),
                headers.size(),
                pageEnd,
                request == null ? null : request.sectionTitle(),
                null,
                rowCounter);
        return new NormalizationResult(true, tableName, outRows, summary, state, null, 0.95, stats);
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

    public SuppressionProfile buildSuppressionProfile(List<RawTableModel> rawTables) {
        if (rawTables == null || rawTables.isEmpty()) {
            return SuppressionProfile.empty();
        }
        Set<String> cells = new LinkedHashSet<>();
        for (RawTableModel table : rawTables) {
            if (table == null || table.rows() == null) {
                continue;
            }
            for (RawTableRow row : table.rows()) {
                for (RawTableCell cell : row.cells()) {
                    addTokensFromCell(cell.text(), cells, 3);
                }
            }
        }
        return new SuppressionProfile(Set.copyOf(cells), Set.of());
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
            return new HeaderInference(state.originalHeaders(), attributionFromHeaders(state.originalHeaders()), dataStart, 0, 0);
        }
        if (parsed.separatorIndex() > start) {
            List<Integer> explicitHeaderRows = new ArrayList<>();
            for (int i = start; i < parsed.separatorIndex(); i++) {
                if (countNonEmpty(rows.get(i)) > 0 && !isSeparatorRow(rows.get(i))) {
                    explicitHeaderRows.add(i);
                }
            }
            if (!explicitHeaderRows.isEmpty()) {
                HeaderAttribution attribution = composeHeaderSlots(rows, explicitHeaderRows, maxCols);
                List<String> headers = headersFromSlots(attribution.slots());
                int generic = (int) headers.stream().filter(NormalizedTableService::isGenericHeader).count();
                int dataStart = parsed.separatorIndex() + 1;
                while (dataStart < rows.size()
                        && (isSeparatorRow(rows.get(dataStart)) || countNonEmpty(rows.get(dataStart)) == 0)) {
                    dataStart++;
                }
                return new HeaderInference(
                        headers, attribution, dataStart, Math.max(0, explicitHeaderRows.size() - 1), generic);
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

        HeaderAttribution attribution = composeHeaderSlots(rows, headerIndexes, maxCols);
        List<String> headers = headersFromSlots(attribution.slots());
        int dataStart = headerIndexes.get(headerIndexes.size() - 1) + 1;
        while (dataStart < rows.size()
                && (isSeparatorRow(rows.get(dataStart)) || countNonEmpty(rows.get(dataStart)) == 0)) {
            dataStart++;
        }
        int generic = (int) headers.stream().filter(NormalizedTableService::isGenericHeader).count();
        return new HeaderInference(headers, attribution, dataStart, Math.max(0, headerIndexes.size() - 1), generic);
    }

    private static HeaderAttribution composeHeaderSlots(List<List<String>> rows, List<Integer> headerIndexes, int maxCols) {
        if (rows == null || headerIndexes == null || headerIndexes.isEmpty() || maxCols <= 0) {
            return HeaderAttribution.empty();
        }
        List<HeaderSlot> slots = new ArrayList<>();
        int prevented = 0;
        int fallback = 0;
        int beforeTokens = 0;
        int afterTokens = 0;
        int noisyBefore = 0;
        int noisyAfter = 0;
        int fragmentsWithoutCoordinates = 0;
        Set<String> contextualFragments = new LinkedHashSet<>();
        for (int col = 0; col < maxCols; col++) {
            List<String> fragments = new ArrayList<>();
            for (Integer idx : headerIndexes) {
                List<String> row = rows.get(idx);
                String val = col < row.size() ? safe(row.get(col)).replaceAll("\\s+", " ") : "";
                if (!val.isBlank() && !isSeparatorText(val)) {
                    fragments.add(val);
                    fragmentsWithoutCoordinates++;
                }
            }
            String before = String.join(" ", fragments).trim().replaceAll("\\s+", " ");
            String selected = selectFocusedHeader(fragments, col);
            boolean generic = isGenericHeader(selected);
            beforeTokens += tokenCount(before);
            afterTokens += tokenCount(selected);
            if (isNoisyHeader(before, fragments.size())) {
                noisyBefore++;
            }
            if (isNoisyHeader(selected, 1)) {
                noisyAfter++;
            }
            if (fragments.size() > 1 && !selected.equals(before) && !generic) {
                prevented++;
            }
            if (generic) {
                fallback++;
                fragments.stream()
                    .filter(NormalizedTableService::looksLikeContextualHeaderFragment)
                    .forEach(contextualFragments::add);
            }
            slots.add(new HeaderSlot(
                    col,
                    selected,
                    List.copyOf(fragments),
                    generic ? 0.35 : fragments.size() > 1 ? 0.85 : 0.95,
                    generic,
                    selectedSourceFragmentCount(selected, fragments),
                    false));
        }
        SpanAlignmentResult spanResult = applySpanAwareFallbacks(slots);
        List<HeaderSlot> uniqueSlots = disambiguateSlots(spanResult.slots());
        int n = Math.max(1, maxCols);
        return new HeaderAttribution(
                uniqueSlots,
                prevented,
                fallback + spanResult.compactFallbackCount(),
                spanResult.compactSuspiciousCount(),
                spanResult.compactFallbackCount(),
                0,
                spanResult.multiColumnRejectedCount(),
                0,
                fragmentsWithoutCoordinates,
                (double) beforeTokens / n,
                (double) uniqueSlots.stream().mapToInt(s -> tokenCount(s.selectedHeader())).sum() / n,
                noisyBefore,
                (int) uniqueSlots.stream().filter(s -> isNoisyHeader(s.selectedHeader(), 1)).count(),
                List.copyOf(contextualFragments));
    }

    private record SpanAlignmentResult(
            List<HeaderSlot> slots,
            int compactSuspiciousCount,
            int compactFallbackCount,
            int multiColumnRejectedCount
    ) {}

    private static SpanAlignmentResult applySpanAwareFallbacks(List<HeaderSlot> slots) {
        if (slots == null || slots.isEmpty()) {
            return new SpanAlignmentResult(List.of(), 0, 0, 0);
        }
        List<Integer> tokenCounts = slots.stream()
                .map(HeaderSlot::selectedHeader)
                .filter(h -> !isGenericHeader(h))
                .map(NormalizedTableService::tokenCount)
                .sorted()
                .toList();
        double median = tokenCounts.isEmpty() ? 0.0 : tokenCounts.get(tokenCounts.size() / 2);
        List<HeaderSlot> out = new ArrayList<>();
        int suspicious = 0;
        int fallback = 0;
        int rejected = 0;
        for (HeaderSlot slot : slots) {
            boolean compactSuspicious = isCompactMultiColumnCandidate(slot, slots, median);
            if (compactSuspicious) {
                suspicious++;
                fallback++;
                rejected++;
                out.add(new HeaderSlot(
                        slot.columnIndex(),
                        "col_" + (slot.columnIndex() + 1),
                        slot.rawHeaderFragments(),
                        0.30,
                        true,
                        slot.selectedSourceFragmentCount(),
                        true));
            } else {
                out.add(slot);
            }
        }
        return new SpanAlignmentResult(List.copyOf(out), suspicious, fallback, rejected);
    }

    private static boolean isCompactMultiColumnCandidate(HeaderSlot slot, List<HeaderSlot> allSlots, double medianTokenCount) {
        if (slot == null || isGenericHeader(slot.selectedHeader())) {
            return false;
        }
        String header = safe(slot.selectedHeader());
        int tokens = tokenCount(header);
        if (tokens < 2) {
            return false;
        }
        boolean containsOtherHeader = false;
        boolean overlapsOtherHeader = false;
        boolean duplicateAdjacent = false;
        List<String> candidateTokens = normalizedTokens(header);
        for (HeaderSlot other : allSlots) {
            if (other == null || other.columnIndex() == slot.columnIndex() || isGenericHeader(other.selectedHeader())) {
                continue;
            }
            String otherHeader = safe(other.selectedHeader());
            if (normalizeForMatch(header).equals(normalizeForMatch(otherHeader))) {
                duplicateAdjacent = Math.abs(other.columnIndex() - slot.columnIndex()) == 1;
                continue;
            }
            if (tokenSubsequenceContains(header, otherHeader) && tokenCount(otherHeader) < tokens) {
                containsOtherHeader = true;
                break;
            }
            List<String> otherTokens = normalizedTokens(otherHeader);
            if (tokens >= 2 && otherTokens.size() >= 2 && sharesHeaderToken(candidateTokens, otherTokens)) {
                overlapsOtherHeader = true;
            }
        }
        if (duplicateAdjacent) {
            return true;
        }
        if (containsOtherHeader) {
            return true;
        }
        if (overlapsOtherHeader) {
            return true;
        }
        if (tokens >= 2 && hasDataLikeHeaderToken(header)) {
            return true;
        }
        return medianTokenCount > 0.0 && tokens >= Math.max(4, Math.ceil(medianTokenCount * 2.0))
                && slot.selectedSourceFragmentCount() > 1;
    }

    private static boolean sharesHeaderToken(List<String> left, List<String> right) {
        if (left == null || right == null || left.isEmpty() || right.isEmpty()) {
            return false;
        }
        Set<String> rightSet = new HashSet<>(right);
        for (String token : left) {
            if (token.length() >= 2 && rightSet.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasDataLikeHeaderToken(String header) {
        for (String token : safe(header).split("\\s+")) {
            String normalized = normalizeForMatch(token);
            if (normalized.matches("\\d+")
                    || normalized.matches("\\d+[-/.]\\d+")
                    || normalized.matches("\\d{1,2}[-/.]\\d{1,2}[-/.]\\d{2,4}")) {
                return true;
            }
        }
        return false;
    }

    private static int selectedSourceFragmentCount(String selected, List<String> fragments) {
        if (selected == null || fragments == null || fragments.isEmpty()) {
            return 0;
        }
        String normalizedSelected = normalizeForMatch(selected);
        int count = 0;
        for (String fragment : fragments) {
            String cleaned = cleanSameColumnHeader(fragment);
            if (normalizeForMatch(cleaned).equals(normalizedSelected)) {
                count++;
            }
        }
        return Math.max(1, count);
    }

    private static boolean tokenSubsequenceContains(String container, String contained) {
        List<String> haystack = normalizedTokens(container);
        List<String> needle = normalizedTokens(contained);
        if (haystack.isEmpty() || needle.isEmpty() || needle.size() >= haystack.size()) {
            return false;
        }
        for (int i = 0; i <= haystack.size() - needle.size(); i++) {
            boolean match = true;
            for (int j = 0; j < needle.size(); j++) {
                if (!haystack.get(i + j).equals(needle.get(j))) {
                    match = false;
                    break;
                }
            }
            if (match) {
                return true;
            }
        }
        return false;
    }

    private static List<String> normalizedTokens(String text) {
        String normalized = normalizeForMatch(text);
        if (normalized.isBlank()) {
            return List.of();
        }
        return List.of(normalized.split("\\s+"));
    }

    private static List<String> headersFromSlots(List<HeaderSlot> slots) {
        if (slots == null || slots.isEmpty()) {
            return List.of();
        }
        return slots.stream().map(HeaderSlot::selectedHeader).toList();
    }

    private static String selectFocusedHeader(List<String> fragments, int columnIndex) {
        if (fragments == null || fragments.isEmpty()) {
            return "col_" + (columnIndex + 1);
        }
        for (int i = fragments.size() - 1; i >= 0; i--) {
            String candidate = cleanSameColumnHeader(fragments.get(i));
            if (!candidate.isBlank() && !isSeparatorText(candidate)) {
                if (isAmbiguousSingleColumnHeader(candidate)) {
                    return "col_" + (columnIndex + 1);
                }
                return candidate;
            }
        }
        return "col_" + (columnIndex + 1);
    }

    private static String cleanSameColumnHeader(String header) {
        if (header == null || header.isBlank()) {
            return "";
        }
        List<String> tokens = new ArrayList<>(List.of(header.trim().replaceAll("\\s+", " ").split(" ")));
        tokens = removeAdjacentDuplicateHeaderTokens(tokens);
        tokens = removeRepeatedHeaderHalves(tokens);
        return String.join(" ", tokens).trim();
    }

    private static boolean isAmbiguousSingleColumnHeader(String header) {
        if (header == null || header.isBlank() || isGenericHeader(header)) {
            return true;
        }
        List<String> tokens = List.of(header.trim().replaceAll("\\s+", " ").split(" "));
        Set<String> unique = new LinkedHashSet<>();
        for (String token : tokens) {
            unique.add(normalizeForMatch(token));
        }
        return tokens.size() >= 6 || unique.size() < tokens.size();
    }

    private static List<String> removeAdjacentDuplicateHeaderTokens(List<String> tokens) {
        List<String> out = new ArrayList<>();
        String previous = null;
        for (String token : tokens) {
            String normalized = normalizeForMatch(token);
            if (!normalized.equals(previous)) {
                out.add(token);
            }
            previous = normalized;
        }
        return out;
    }

    private static List<String> removeRepeatedHeaderHalves(List<String> tokens) {
        if (tokens == null || tokens.size() % 2 != 0 || tokens.size() < 2) {
            return tokens == null ? List.of() : tokens;
        }
        int half = tokens.size() / 2;
        for (int i = 0; i < half; i++) {
            if (!normalizeForMatch(tokens.get(i)).equals(normalizeForMatch(tokens.get(i + half)))) {
                return tokens;
            }
        }
        return new ArrayList<>(tokens.subList(0, half));
    }

    private static List<HeaderSlot> disambiguateSlots(List<HeaderSlot> slots) {
        Map<String, Integer> seen = new HashMap<>();
        List<HeaderSlot> out = new ArrayList<>();
        for (HeaderSlot slot : slots) {
            String h = safe(slot.selectedHeader());
            if (h.isBlank()) {
                h = "col_" + (slot.columnIndex() + 1);
            }
            String key = normalizeForMatch(h);
            int count = seen.getOrDefault(key, 0) + 1;
            seen.put(key, count);
            String unique = count == 1 ? h : h + "_" + count;
            out.add(new HeaderSlot(slot.columnIndex(), unique, slot.rawHeaderFragments(),
                    slot.confidence(), slot.fallbackGeneric(),
                    slot.selectedSourceFragmentCount(), slot.multiColumnRejected()));
        }
        return out;
    }

    private static HeaderAttribution attributionFromHeaders(List<String> headers) {
        if (headers == null || headers.isEmpty()) {
            return HeaderAttribution.empty();
        }
        List<HeaderSlot> slots = new ArrayList<>();
        int generic = 0;
        int tokens = 0;
        int noisy = 0;
        for (int i = 0; i < headers.size(); i++) {
            String header = safe(headers.get(i));
            boolean fallback = isGenericHeader(header);
            if (fallback) {
                generic++;
            }
            tokens += tokenCount(header);
            if (isNoisyHeader(header, 1)) {
                noisy++;
            }
            slots.add(new HeaderSlot(i, header.isBlank() ? "col_" + (i + 1) : header,
                    header.isBlank() ? List.of() : List.of(header), fallback ? 0.35 : 0.95, fallback,
                    header.isBlank() ? 0 : 1, false));
        }
        double avg = (double) tokens / Math.max(1, headers.size());
        return new HeaderAttribution(slots, 0, generic, 0, 0, 0, 0, 0, headers.size(),
                avg, avg, noisy, noisy, List.of());
    }

    private static boolean looksLikeContextualHeaderFragment(String fragment) {
        String text = safe(fragment).replaceAll("\\s+", " ");
        if (text.length() < 12 || !text.contains(":")) {
            return false;
        }
        int tokens = tokenCount(text);
        int punctuation = 0;
        for (char ch : text.toCharArray()) {
            if (ch == ':' || ch == ',' || ch == ';') {
                punctuation++;
            }
        }
        return tokens >= 3 && punctuation > 0;
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

    private static int tokenCount(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return text.trim().split("\\s+").length;
    }

    private static boolean isNoisyHeader(String header, int fragmentCount) {
        if (header == null || header.isBlank() || isGenericHeader(header)) {
            return false;
        }
        List<String> tokens = List.of(header.trim().replaceAll("\\s+", " ").split(" "));
        Set<String> unique = new LinkedHashSet<>();
        for (String token : tokens) {
            unique.add(normalizeForMatch(token));
        }
        return fragmentCount > 1 || tokens.size() >= 6 || unique.size() < tokens.size();
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

    /** Fragment text together with the source cell's x-range; used for broad-spanning detection. */
    private record CoordFragment(String text, double srcX, double srcXEnd) {}

    /** Return value of {@link #inferHeadersFromCoordinates} carrying both slots and diagnostic counts. */
    private record CoordInferenceResult(
            List<CoordinateHeaderSlot> slots,
            int zeroOverlapRejected,
            int broadSpanningDemoted,
            int collisionSuffixPrevented
    ) {}

    private record CoordinateHeaderSlot(
            int columnIndex,
            String header,
            double x,
            double xEnd,
            boolean fallbackGeneric,
            List<String> fragments
    ) {}

    private record CoordinateLogicalRow(
            Map<Integer, List<RawTableCell>> cellsByColumn,
            int pageNumber
    ) {}

    private record CoordinateMergeResult(
            List<CoordinateLogicalRow> rows,
            int attempts,
            int acceptedMerges,
            int rejectedByNewIdentifier,
            int rejectedByNewRowNumber,
            int rejectedByXOverlap
    ) {}

    private static int firstSubstantiveRawRow(List<RawTableRow> rows) {
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).cells().stream().anyMatch(c -> c.text() != null && !c.text().isBlank())) {
                return i;
            }
        }
        return 0;
    }

    private static int inferRawDataStart(List<RawTableRow> rows, int scanFrom, int maxCols) {
        int limit = Math.min(rows.size(), scanFrom + 8);
        int lastHeader = scanFrom - 1;
        for (int i = scanFrom; i < limit; i++) {
            RawTableRow row = rows.get(i);
            if (isRawBlankRow(row)) {
                continue;
            }
            if (isStrongRawDataRow(row, maxCols)) {
                return i;
            }
            if (countNonEmptyRaw(row) >= 1) {
                lastHeader = i;
            }
        }
        if (rows.size() - scanFrom >= 3) {
            return Math.min(rows.size(), scanFrom + 2);
        }
        return Math.min(rows.size(), scanFrom + 1);
    }

    private static boolean isRawBlankRow(RawTableRow row) {
        return row == null || row.cells() == null
                || row.cells().stream().allMatch(c -> c.text() == null || c.text().isBlank());
    }

    private static int countNonEmptyRaw(RawTableRow row) {
        if (row == null || row.cells() == null) {
            return 0;
        }
        int count = 0;
        for (RawTableCell cell : row.cells()) {
            if (cell.text() != null && !cell.text().isBlank()) {
                count++;
            }
        }
        return count;
    }

    private static boolean isStrongRawDataRow(RawTableRow row, int maxCols) {
        if (row == null || row.cells() == null) {
            return false;
        }
        int nonEmpty = countNonEmptyRaw(row);
        if (nonEmpty < 2) {
            return false;
        }
        for (RawTableCell cell : row.cells()) {
            String text = safe(cell.text());
            if (text.isBlank()) {
                continue;
            }
            if (cell.physicalColIndex() <= 1 && ROW_NUMBER_START.matcher(text).matches()) {
                return true;
            }
            if (cell.physicalColIndex() <= Math.min(4, maxCols - 1) && looksLikeIdentifierToken(text)) {
                return true;
            }
        }
        int numeric = 0;
        for (RawTableCell cell : row.cells()) {
            String text = safe(cell.text());
            if (!text.isBlank() && text.matches("^\\d+([.,]\\d+)?$")) {
                numeric++;
            }
        }
        return maxCols >= 3 && numeric >= Math.max(2, nonEmpty / 2);
    }

    private static CoordInferenceResult inferHeadersFromCoordinates(
            RawTableModel table,
            int headerStart,
            int dataStart,
            int maxCols
    ) {
        // ---- Step 1: compute slot x-ranges from data cells ----
        double[] slotX    = new double[maxCols];
        double[] slotXEnd = new double[maxCols];
        java.util.Arrays.fill(slotX, Double.MAX_VALUE);
        java.util.Arrays.fill(slotXEnd, 0.0);
        for (int col = 0; col < maxCols; col++) {
            for (int r = Math.max(0, dataStart); r < table.rows().size(); r++) {
                RawTableCell cell = cellAt(table.rows().get(r), col);
                if (cell != null && cell.hasCoordinates()) {
                    slotX[col]    = Math.min(slotX[col],    cell.x());
                    slotXEnd[col] = Math.max(slotXEnd[col], cell.xEnd());
                }
            }
            if (slotX[col] == Double.MAX_VALUE) {
                for (RawTableRow row : table.rows()) {
                    RawTableCell cell = cellAt(row, col);
                    if (cell != null && cell.hasCoordinates()) {
                        slotX[col]    = Math.min(slotX[col],    cell.x());
                        slotXEnd[col] = Math.max(slotXEnd[col], cell.xEnd());
                    }
                }
            }
            if (slotX[col] == Double.MAX_VALUE) {
                slotX[col]    = col;
                slotXEnd[col] = col + 1.0;
            }
        }

        // ---- Step 2: collect fragments with zero-overlap rejection and broad classification ----
        // Fix 1: fragments with overlap <= 0 are rejected (they come from nearest-center fallback
        //        and would introduce wrong repeated parent keys across unrelated columns).
        // Fix 3: fragments are classified as child (narrow, well-aligned) or parent (broad spanning).
        int zeroOverlapRejected = 0;
        int broadSpanningCandidates = 0;
        List<List<CoordFragment>> childFragsPerSlot  = new ArrayList<>();
        List<List<CoordFragment>> parentFragsPerSlot = new ArrayList<>();

        for (int col = 0; col < maxCols; col++) {
            double sx = slotX[col], sx2 = slotXEnd[col];
            double slotWidth = sx2 - sx;
            List<CoordFragment> childFrags  = new ArrayList<>();
            List<CoordFragment> parentFrags = new ArrayList<>();

            for (int r = Math.max(0, headerStart); r < Math.min(dataStart, table.rows().size()); r++) {
                RawTableRow headerRow = table.rows().get(r);
                RawTableCell best = bestHeaderCellForSlot(headerRow, sx, sx2, col);
                if (best == null || best.text() == null || best.text().isBlank()) continue;

                double ov = overlap(best.x(), best.xEnd(), sx, sx2);
                if (ov <= 0.0) {
                    // Fix 1: reject zero-overlap — prevents wrong parent keys from being inherited
                    zeroOverlapRejected++;
                    continue;
                }

                String fragText = best.text().trim().replaceAll("\\s+", " ");
                CoordFragment frag = new CoordFragment(fragText, best.x(), best.xEnd());

                // Fix 3: classify by cell width relative to slot width
                double srcWidth = best.xEnd() - best.x();
                boolean isBroad = slotWidth > 0.0 && srcWidth > slotWidth * BROAD_HEADER_CELL_FACTOR;
                if (isBroad) {
                    parentFrags.add(frag);
                    broadSpanningCandidates++;
                } else {
                    childFrags.add(frag);
                }
            }
            childFragsPerSlot.add(childFrags);
            parentFragsPerSlot.add(parentFrags);
        }

        // ---- Step 3: select final header per slot (child preferred over broad parent) ----
        // Fix 3: if child fragments exist, use them; fall back to parent only when no child.
        List<CoordinateHeaderSlot> rawSlots = new ArrayList<>();
        int broadFallbackUsed = 0;

        for (int col = 0; col < maxCols; col++) {
            double sx = slotX[col], sx2 = slotXEnd[col];
            List<CoordFragment> childFrags  = childFragsPerSlot.get(col);
            List<CoordFragment> parentFrags = parentFragsPerSlot.get(col);

            // Fix 3: prefer child (non-broad) over broad parent
            List<CoordFragment> preferred = childFrags.isEmpty() ? parentFrags : childFrags;
            if (childFrags.isEmpty() && !parentFrags.isEmpty()) broadFallbackUsed++;

            // Collect all fragment texts for diagnostic/metadata
            List<String> allFragsText = new ArrayList<>();
            childFrags.forEach(f -> allFragsText.add(f.text()));
            parentFrags.forEach(f -> { if (!allFragsText.contains(f.text())) allFragsText.add(f.text()); });

            String selected = "";
            for (int i = preferred.size() - 1; i >= 0; i--) {
                String candidate = cleanSameColumnHeader(preferred.get(i).text());
                if (!candidate.isBlank() && !isAmbiguousSingleColumnHeader(candidate)) {
                    selected = candidate;
                    break;
                }
            }
            boolean fallback = selected.isBlank();
            if (fallback) selected = "col_" + (col + 1);

            rawSlots.add(new CoordinateHeaderSlot(col, selected, sx, sx2, fallback, List.copyOf(allFragsText)));
        }

        // ---- Step 4: demote broad spanning parent headers reused across adjacent slots ----
        // Fix 2 + Fix 4: if the same non-generic header text appears in >= BROAD_HEADER_REUSE_MIN
        // consecutive slots (before disambiguation), it came from one broad spanning parent cell and
        // must not be collision-suffixed into key, key_2, key_3, … — replace entire run with col_N.
        BroadDemotionResult demotionResult = applyBroadSpanningFallback(rawSlots);
        List<CoordinateHeaderSlot> slots = disambiguateCoordinateSlots(demotionResult.slots());

        return new CoordInferenceResult(
                slots,
                zeroOverlapRejected,
                demotionResult.demotedCount(),
                demotionResult.collisionSuffixPrevented());
    }

    private record BroadDemotionResult(
            List<CoordinateHeaderSlot> slots,
            int demotedCount,
            int collisionSuffixPrevented
    ) {}

    /**
     * Replace runs of {@code >= BROAD_HEADER_REUSE_MIN} adjacent slots that share the same
     * non-generic header with generic {@code col_N} fallbacks.
     *
     * <p>This prevents one broad spanning parent cell from producing {@code key}, {@code key_2},
     * {@code key_3}, … across many structurally distinct columns.</p>
     */
    private static BroadDemotionResult applyBroadSpanningFallback(List<CoordinateHeaderSlot> slots) {
        if (slots == null || slots.size() < BROAD_HEADER_REUSE_MIN) {
            return new BroadDemotionResult(
                    slots == null ? List.of() : List.copyOf(slots), 0, 0);
        }
        int n = slots.size();
        boolean[] demote = new boolean[n];
        int i = 0;
        while (i < n) {
            CoordinateHeaderSlot s = slots.get(i);
            if (s.fallbackGeneric()) { i++; continue; }
            String norm = normalizeForMatch(s.header());
            if (norm.isBlank()) { i++; continue; }

            int j = i + 1;
            while (j < n && !slots.get(j).fallbackGeneric()
                    && normalizeForMatch(slots.get(j).header()).equals(norm)) {
                j++;
            }
            int runLen = j - i;
            if (runLen >= BROAD_HEADER_REUSE_MIN) {
                for (int k = i; k < j; k++) demote[k] = true;
            }
            i = j;
        }

        List<CoordinateHeaderSlot> result = new ArrayList<>();
        int demoted = 0;
        for (int k = 0; k < n; k++) {
            CoordinateHeaderSlot slot = slots.get(k);
            if (demote[k]) {
                result.add(new CoordinateHeaderSlot(
                        slot.columnIndex(), "col_" + (slot.columnIndex() + 1),
                        slot.x(), slot.xEnd(), true, slot.fragments()));
                demoted++;
            } else {
                result.add(slot);
            }
        }
        return new BroadDemotionResult(List.copyOf(result), demoted, demoted);
    }

    private static RawTableCell bestHeaderCellForSlot(RawTableRow row, double x, double xEnd, int fallbackCol) {
        if (row == null || row.cells() == null) {
            return null;
        }
        RawTableCell best = null;
        double bestScore = 0.0;
        double center = (x + xEnd) / 2.0;
        for (RawTableCell cell : row.cells()) {
            String text = safe(cell.text());
            if (text.isBlank()) {
                continue;
            }
            double overlap = overlap(cell.x(), cell.xEnd(), x, xEnd);
            double score = overlap > 0.0 ? overlap : 1.0 / (1.0 + Math.abs(cell.centerX() - center));
            if (score > bestScore) {
                best = cell;
                bestScore = score;
            }
        }
        if (best == null) {
            best = cellAt(row, fallbackCol);
        }
        return best;
    }

    private static List<CoordinateHeaderSlot> disambiguateCoordinateSlots(List<CoordinateHeaderSlot> slots) {
        Map<String, Integer> seen = new HashMap<>();
        List<CoordinateHeaderSlot> out = new ArrayList<>();
        for (CoordinateHeaderSlot slot : slots) {
            String header = safe(slot.header()).isBlank() ? "col_" + (slot.columnIndex() + 1) : slot.header();
            String key = normalizeForMatch(header);
            int count = seen.getOrDefault(key, 0) + 1;
            seen.put(key, count);
            String unique = count == 1 ? header : header + "_" + count;
            out.add(new CoordinateHeaderSlot(
                    slot.columnIndex(), unique, slot.x(), slot.xEnd(), slot.fallbackGeneric(), slot.fragments()));
        }
        return List.copyOf(out);
    }

    private static CoordinateMergeResult mergeWrappedRowsWithCoordinateGuard(
            List<RawTableRow> physicalRows,
            List<CoordinateHeaderSlot> slots
    ) {
        List<CoordinateLogicalRow> merged = new ArrayList<>();
        int attempts = 0;
        int accepted = 0;
        int rejectId = 0;
        int rejectNum = 0;
        int rejectX = 0;
        double medianHeight = medianCellHeight(physicalRows);
        double previousY = -1.0;
        for (RawTableRow row : physicalRows) {
            if (isRawBlankRow(row)) {
                continue;
            }
            CoordinateLogicalRow current = toLogicalRow(row, slots);
            if (merged.isEmpty()) {
                merged.add(current);
                previousY = rowY(row);
                continue;
            }
            attempts++;
            MergeDecision decision = coordinateContinuationDecision(row, current, slots, previousY, medianHeight);
            if (decision == MergeDecision.ACCEPT) {
                mergeLogicalRows(merged.get(merged.size() - 1), current);
                accepted++;
            } else {
                if (decision == MergeDecision.NEW_IDENTIFIER) {
                    rejectId++;
                } else if (decision == MergeDecision.NEW_ROW_NUMBER) {
                    rejectNum++;
                } else if (decision == MergeDecision.X_OVERLAP) {
                    rejectX++;
                }
                merged.add(current);
            }
            previousY = rowY(row);
        }
        return new CoordinateMergeResult(List.copyOf(merged), attempts, accepted, rejectId, rejectNum, rejectX);
    }

    private enum MergeDecision { ACCEPT, NEW_IDENTIFIER, NEW_ROW_NUMBER, X_OVERLAP, NEW_ROW_SHAPE }

    private static MergeDecision coordinateContinuationDecision(
            RawTableRow row,
            CoordinateLogicalRow logicalRow,
            List<CoordinateHeaderSlot> slots,
            double previousY,
            double medianHeight
    ) {
        int nonEmpty = countNonEmptyRaw(row);
        if (hasRowNumberInLeadingColumns(row)) {
            return MergeDecision.NEW_ROW_NUMBER;
        }
        if (hasIdentifierInLeadingColumns(row)) {
            return MergeDecision.NEW_IDENTIFIER;
        }
        if (nonEmpty == 0) {
            return MergeDecision.NEW_ROW_SHAPE;
        }
        if (nonEmpty >= 3) {
            return MergeDecision.NEW_ROW_SHAPE;
        }
        if (nonEmpty == 1) {
            return MergeDecision.ACCEPT;
        }
        double yGap = previousY < 0.0 ? 0.0 : Math.abs(rowY(row) - previousY);
        if (medianHeight > 0.0 && yGap > medianHeight * 1.8) {
            return MergeDecision.NEW_ROW_SHAPE;
        }
        for (RawTableCell cell : row.cells()) {
            if (safe(cell.text()).isBlank()) {
                continue;
            }
            CoordinateHeaderSlot slot = bestSlotForCell(cell, slots);
            if (slot == null || overlap(cell.x(), cell.xEnd(), slot.x(), slot.xEnd()) <= 0.0) {
                return MergeDecision.X_OVERLAP;
            }
        }
        return MergeDecision.ACCEPT;
    }

    private static CoordinateLogicalRow toLogicalRow(RawTableRow row, List<CoordinateHeaderSlot> slots) {
        Map<Integer, List<RawTableCell>> byColumn = new LinkedHashMap<>();
        for (RawTableCell cell : row.cells()) {
            if (safe(cell.text()).isBlank()) {
                continue;
            }
            int col;
            if (cell.extractorType() == RawTableModel.ExtractorType.DOCX
                    && cell.physicalColIndex() >= 0
                    && cell.physicalColIndex() < slots.size()) {
                // DOCX logical-grid: physicalColIndex is the definitive column assignment.
                // Coordinate x-overlap is not reliable for DOCX because merged group-header
                // rows within the table body can contaminate slot x-ranges, causing all cells
                // in a row to be falsely packed into slot 0 via tie-broken overlap scoring.
                col = cell.physicalColIndex();
            } else {
                CoordinateHeaderSlot slot = bestSlotForCell(cell, slots);
                col = slot == null ? cell.physicalColIndex() : slot.columnIndex();
            }
            byColumn.computeIfAbsent(col, ignored -> new ArrayList<>()).add(cell);
        }
        int page = row.cells().isEmpty() ? 1 : row.cells().get(0).pageNumber();
        return new CoordinateLogicalRow(byColumn, page);
    }

    private static void mergeLogicalRows(CoordinateLogicalRow previous, CoordinateLogicalRow continuation) {
        for (Map.Entry<Integer, List<RawTableCell>> entry : continuation.cellsByColumn().entrySet()) {
            previous.cellsByColumn().computeIfAbsent(entry.getKey(), ignored -> new ArrayList<>()).addAll(entry.getValue());
        }
    }

    private static Map<String, String> mapCellsFromCoordinates(
            CoordinateLogicalRow row,
            List<CoordinateHeaderSlot> slots
    ) {
        Map<String, String> cells = new LinkedHashMap<>();
        for (CoordinateHeaderSlot slot : slots) {
            List<RawTableCell> values = row.cellsByColumn().getOrDefault(slot.columnIndex(), List.of());
            String value = values.stream()
                    .map(RawTableCell::text)
                    .filter(v -> v != null && !v.isBlank())
                    .map(v -> v.trim().replaceAll("\\s+", " "))
                    .reduce((a, b) -> a + " " + b)
                    .orElse("");
            cells.put(slot.header(), value);
        }
        return cells;
    }

    private static CoordinateHeaderSlot bestSlotForCell(RawTableCell cell, List<CoordinateHeaderSlot> slots) {
        CoordinateHeaderSlot best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (CoordinateHeaderSlot slot : slots) {
            double overlap = overlap(cell.x(), cell.xEnd(), slot.x(), slot.xEnd());
            double score = overlap > 0.0
                    ? overlap
                    : -Math.abs(cell.centerX() - ((slot.x() + slot.xEnd()) / 2.0));
            if (score > bestScore) {
                best = slot;
                bestScore = score;
            }
        }
        return best;
    }

    private static RawTableCell cellAt(RawTableRow row, int col) {
        if (row == null || row.cells() == null || col < 0 || col >= row.cells().size()) {
            return null;
        }
        return row.cells().get(col);
    }

    private static double overlap(double aStart, double aEnd, double bStart, double bEnd) {
        return Math.max(0.0, Math.min(aEnd, bEnd) - Math.max(aStart, bStart));
    }

    private static double medianCellHeight(List<RawTableRow> rows) {
        List<Double> heights = new ArrayList<>();
        for (RawTableRow row : rows) {
            for (RawTableCell cell : row.cells()) {
                if (cell.height() > 0.0) {
                    heights.add(cell.height());
                }
            }
        }
        if (heights.isEmpty()) {
            return 0.0;
        }
        heights.sort(Double::compareTo);
        return heights.get(heights.size() / 2);
    }

    private static double rowY(RawTableRow row) {
        return row.cells().stream()
                .filter(RawTableCell::hasCoordinates)
                .mapToDouble(RawTableCell::y)
                .min()
                .orElse(0.0);
    }

    private static boolean hasRowNumberInLeadingColumns(RawTableRow row) {
        for (RawTableCell cell : row.cells()) {
            if (cell.physicalColIndex() <= 1 && ROW_NUMBER_START.matcher(safe(cell.text())).matches()) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasIdentifierInLeadingColumns(RawTableRow row) {
        for (RawTableCell cell : row.cells()) {
            if (cell.physicalColIndex() <= 4 && looksLikeIdentifierToken(safe(cell.text()))) {
                return true;
            }
        }
        return false;
    }

    private static boolean looksLikeIdentifierToken(String text) {
        if (text == null) {
            return false;
        }
        String t = text.trim();
        return t.matches("(?=.*[A-Z])(?=.*\\d)[A-Z][A-Z0-9._-]{2,20}");
    }

    private static HeaderAttribution coordinateAttribution(List<CoordinateHeaderSlot> slots) {
        List<HeaderSlot> headerSlots = new ArrayList<>();
        int tokens = 0;
        int noisy = 0;
        for (CoordinateHeaderSlot slot : slots) {
            tokens += tokenCount(slot.header());
            if (isNoisyHeader(slot.header(), slot.fragments().size())) {
                noisy++;
            }
            headerSlots.add(new HeaderSlot(
                    slot.columnIndex(),
                    slot.header(),
                    slot.fragments(),
                    slot.fallbackGeneric() ? 0.35 : 0.95,
                    slot.fallbackGeneric(),
                    slot.fragments().isEmpty() ? 0 : 1,
                    false));
        }
        double avg = (double) tokens / Math.max(1, slots.size());
        int fallback = (int) slots.stream().filter(CoordinateHeaderSlot::fallbackGeneric).count();
        return new HeaderAttribution(
                List.copyOf(headerSlots), 0, fallback, 0, 0,
                slots.size(), 0, slots.size(), 0, avg, avg, noisy, noisy, List.of());
    }

    private static QualityStats buildStats(
            List<NormalizedTableRow> rows,
            int genericColumnKeys,
            int continuationRowsMerged,
            int multiRowHeadersMerged,
            int crossPageHeaderCarryCount,
            int sparseRowsRepaired,
            int droppedCellFragments,
            HeaderAttribution headerAttribution,
            int zeroOverlapRejected,
            int broadSpanningDemoted,
            int collisionSuffixPrevented
    ) {
        if (rows == null || rows.isEmpty()) {
            return QualityStats.empty();
        }
        int onlyOne = 0;
        int totalCells = 0;
        int emptyCells = 0;
        int valuesPreserved = 0;
        for (NormalizedTableRow row : rows) {
            int nonEmpty = 0;
            for (String value : row.cells().values()) {
                totalCells++;
                if (value == null || value.isBlank()) {
                    emptyCells++;
                } else {
                    nonEmpty++;
                    valuesPreserved++;
                }
            }
            if (nonEmpty == 1) {
                onlyOne++;
            }
        }
        double emptyRatio = totalCells == 0 ? 0.0 : (double) emptyCells / totalCells;
        HeaderAttribution attribution = headerAttribution == null
                ? HeaderAttribution.empty()
                : headerAttribution;
        int actualGenericKeys = 0;
        for (NormalizedTableRow row : rows) {
            for (String key : row.cells().keySet()) {
                if (isGenericHeader(key)) {
                    actualGenericKeys++;
                }
            }
        }
        return new QualityStats(
                rows.size(), onlyOne, emptyRatio, Math.max(genericColumnKeys, actualGenericKeys),
                continuationRowsMerged, multiRowHeadersMerged, crossPageHeaderCarryCount,
                sparseRowsRepaired, droppedCellFragments,
                attribution.slots().size(),
                (int) attribution.slots().stream().filter(HeaderSlot::fallbackGeneric).count(),
                attribution.siblingContaminationPrevented(),
                attribution.ambiguousFallbackCount(),
                attribution.avgTokenCountBefore(),
                attribution.avgTokenCountAfter(),
                attribution.noisyBeforeCount(),
                attribution.noisyAfterCount(),
                attribution.compactSuspiciousCount(),
                attribution.compactFallbackCount(),
                attribution.spanAwareSelectedCount(),
                attribution.multiColumnRejectedCount(),
                attribution.fragmentsWithCoordinates(),
                attribution.fragmentsWithoutCoordinates(),
                valuesPreserved,
                0,
                zeroOverlapRejected,
                broadSpanningDemoted,
                collisionSuffixPrevented);
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

    private static String joinHeaderContext(List<String> fragments) {
        if (fragments == null || fragments.isEmpty()) {
            return "";
        }
        return fragments.stream()
                .filter(f -> f != null && !f.isBlank())
                .map(f -> f.trim().replaceAll("\\s+", " "))
                .distinct()
                .reduce((a, b) -> a + " " + b)
                .orElse("");
    }

    private static String effectiveGroupContext(String groupContext, String headerContext) {
        if (groupContext != null && !groupContext.isBlank()) {
            return groupContext;
        }
        return headerContext == null || headerContext.isBlank() ? null : headerContext;
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
