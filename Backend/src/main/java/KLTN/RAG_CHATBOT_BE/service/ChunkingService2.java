package KLTN.RAG_CHATBOT_BE.service;

import KLTN.RAG_CHATBOT_BE.record.DocumentChunk;
import KLTN.RAG_CHATBOT_BE.record.Section;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ChunkingService2 {

    private final NormalizedTableService normalizedTableService;

    /** Unit tests without Spring. */
    public ChunkingService2() {
        this(new NormalizedTableService());
    }

    private final TableIngestMetrics lastIngestMetrics = new TableIngestMetrics();

    private static final int MAX_CHARS_PER_TEXT_CHUNK = 2200;
    private static final int OVERLAP_CHARS = 250;
    private static final int SECTION_SUMMARY_THRESHOLD = MAX_CHARS_PER_TEXT_CHUNK * 2;
    // Số cột tối thiểu để coi một dòng là "dạng bảng" (pseudo-table)
    private static final int PSEUDO_TABLE_MIN_COLS = 3;
    // Số dòng tối thiểu liên tiếp để coi là một pseudo-table
    private static final int PSEUDO_TABLE_MIN_ROWS = 3;

    private static final Pattern SECTION_NUMBER_PATTERN =
            Pattern.compile("^(\\d+(?:\\.\\d+)*)[.\\s]");
    private static final Pattern RAW_TABLE_REF_PATTERN =
            Pattern.compile("^\\[RAW_TABLE_REF:([^\\]]+)]$");

    // ===================================================================
    // PUBLIC ENTRY POINT
    // ===================================================================

    public TableIngestMetrics getLastIngestMetrics() {
        return lastIngestMetrics;
    }

    public List<DocumentChunk> processSections2(List<Section> sections) {
        if (sections == null || sections.isEmpty()) {
            return List.of();
        }

        lastIngestMetrics.reset();

        // Phase 0: Pre-compute parent-child relationships từ danh sách sections
        Map<String, List<Section>> parentToDirectChildren = buildParentChildMap(sections);
        log.info("[Chunk] Parent sections detected: {} (with at least one direct child)",
                parentToDirectChildren.size());

        List<DocumentChunk> finalChunks = new ArrayList<>();
        int globalOrder = 0;
        List<HeadingNode> headingStack = new ArrayList<>();

        for (int sectionIndex = 0; sectionIndex < sections.size(); sectionIndex++) {
            Section section = sections.get(sectionIndex);
            int sectionOrder = section.orderIndex();
            int headingLevel = section.headingLevel();

            String sectionNumber = extractSectionNumber(section.header());
            String sectionId = sectionNumber != null
                    ? "sec_" + sectionNumber
                    : "sec_idx_" + sectionIndex;
            String parentId = sectionNumber != null
                    ? "parent_" + extractParentSectionNumber(sectionNumber)
                    : "parent_idx_" + sectionIndex;
            String headingPathText = buildHeadingPathText(headingStack, section.header(), sectionNumber, sectionIndex);

            log.debug("[Chunk] Section start: idx={} order={} id={} parentId={} level={} header='{}' path='{}'",
                    sectionIndex, sectionOrder, sectionId, parentId, headingLevel,
                    safeText(section.header(), ""), headingPathText);

            // --- Phase 1a: Nếu section này là PARENT → tạo parent_section_summary chunk ---
            List<Section> directChildren = parentToDirectChildren.get(sectionId);
            boolean isParentSection = directChildren != null && !directChildren.isEmpty();

            if (isParentSection) {
                String childSectionIds = buildChildSectionIdsStr(directChildren);
                String parentSummaryContent = buildParentSectionSummaryContent(
                        section, directChildren, childSectionIds);

                finalChunks.add(createChunkWithChildren(
                        parentSummaryContent, section, "parent_section_summary",
                        sectionId, parentId, null, headingPathText,
                        globalOrder, sectionOrder, headingLevel, childSectionIds));

                log.debug("[Chunk] +parent_section_summary sectionId={} children=[{}] globalOrder={}",
                        sectionId, childSectionIds, globalOrder);
                globalOrder++;
            }

            // --- Phase 1b: Xử lý nội dung trực tiếp của section (text + table) ---
            // Áp dụng pseudo-table detection nếu section chưa có bảng từ parser
            String enrichedContent = section.content();
            boolean hadExplicitTable = enrichedContent != null
                    && (enrichedContent.contains("[TABLE_START]") || enrichedContent.contains("[RAW_TABLE_REF:"));
            if (!hadExplicitTable) {
                String beforeDetect = enrichedContent;
                enrichedContent = detectAndConvertTextTables(enrichedContent);
                boolean pseudoTableFound = enrichedContent != null
                        && enrichedContent.contains("[TABLE_START]")
                        && (beforeDetect == null || !beforeDetect.contains("[TABLE_START]"));
                if (pseudoTableFound) {
                    log.info("[Chunk] Pseudo-table detected in section '{}' (id={}) — " +
                            "content converted to normalized table chunks.", sectionId, sectionId);
                }
            }

            List<String> segments = splitByTableBlocks(enrichedContent);
            LogicalTableState logicalTableState = null;
            int tableIndexInSection = 0;
            int contentChunksThisSection = 0;
            NormalizedTableService.SuppressionProfile sectionSuppressProfile =
                    buildSectionSuppressionProfile(segments, section);

            // Section summary (cho section dài có nhiều text)
            List<String> textSegments = segments.stream()
                    .filter(s -> !s.startsWith("[TABLE_START]") && !s.startsWith("[RAW_TABLE_REF:"))
                    .toList();
            int totalTextChars = textSegments.stream().mapToInt(String::length).sum();
            if (totalTextChars > SECTION_SUMMARY_THRESHOLD) {
                String sumContent = buildSectionSummary(section.header(), textSegments);
                if (!sumContent.isBlank()) {
                    finalChunks.add(createEnrichedChunk(
                            sumContent, section, "section_summary",
                            sectionId, parentId, null, headingPathText,
                            globalOrder, sectionOrder, headingLevel));
                    globalOrder++;
                    contentChunksThisSection++;
                }
            }

            for (String segment : segments) {
                if (segment.startsWith("[RAW_TABLE_REF:")) {
                    RawTableBlock block = findRawTableBlock(section, segment);
                    if (block == null || block.table() == null) {
                        continue;
                    }
                    lastIngestMetrics.incDetectedTables();
                    lastIngestMetrics.incRawTableModelsCreated(block.table().extractorType());
                    String tableId = "tbl_" + sectionIndex + "_" + globalOrder;
                    int[] orderHolder = { globalOrder, contentChunksThisSection };
                    logicalTableState = processNormalizedRawTable(
                            block.table(),
                            section,
                            sectionId,
                            parentId,
                            tableId,
                            headingPathText,
                            sectionOrder,
                            headingLevel,
                            tableIndexInSection,
                            logicalTableState,
                            finalChunks,
                            orderHolder);
                    globalOrder = orderHolder[0];
                    contentChunksThisSection = orderHolder[1];
                    tableIndexInSection++;
                } else if (segment.startsWith("[TABLE_START]")) {
                    String tableContent = segment
                            .replace("[TABLE_START]", "").replace("[TABLE_END]", "").trim();
                    if (tableContent.isBlank()) {
                        continue;
                    }
                    lastIngestMetrics.incDetectedTables();
                    String tableId = "tbl_" + sectionIndex + "_" + globalOrder;
                    int[] orderHolder = { globalOrder, contentChunksThisSection };
                    logicalTableState = processNormalizedTable(
                            tableContent,
                            section,
                            sectionId,
                            parentId,
                            tableId,
                            headingPathText,
                            sectionOrder,
                            headingLevel,
                            tableIndexInSection,
                            logicalTableState,
                            finalChunks,
                            orderHolder);
                    globalOrder = orderHolder[0];
                    contentChunksThisSection = orderHolder[1];
                    tableIndexInSection++;
                } else {
                    String stripped = stripResidualTableLines(segment);
                    stripped = applySectionSuppression(stripped, sectionSuppressProfile);
                    for (String textChunk : chunkPlainText(stripped)) {
                        if (normalizedTableService.shouldDropLeakyTextChunk(textChunk, sectionSuppressProfile)) {
                            lastIngestMetrics.incDroppedLeakyTextChunks();
                            log.debug("[Chunk] Dropped leaky text chunk in section={} len={}",
                                    sectionId, textChunk.length());
                            continue;
                        }
                        if (textChunk.isBlank()) {
                            continue;
                        }
                        finalChunks.add(createEnrichedChunk(
                                textChunk, section, "text",
                                sectionId, parentId, null, headingPathText,
                                globalOrder, sectionOrder, headingLevel));
                        globalOrder++;
                        contentChunksThisSection++;
                        lastIngestMetrics.incTextChunks();
                    }
                }
            }

            // --- Logging: phân biệt parent rỗng (bình thường) vs section thật sự rỗng ---
            if (contentChunksThisSection == 0) {
                if (isParentSection) {
                    log.info("[Chunk] Parent section '{}' has no direct content; " +
                            "created parent_section_summary from {} children metadata.",
                            sectionId, directChildren.size());
                } else {
                    log.warn("[Chunk] WARNING: Section '{}' has no content and no children — " +
                            "this section will not be indexed via content chunks.",
                            sectionId);
                }
            } else {
                log.debug("[Chunk] Section done: id='{}' level={} contentChunks={} isParent={}",
                        sectionId, headingLevel, contentChunksThisSection, isParentSection);
            }
        }

        // Phase 2: Post-chunking validation
        validateChunks(finalChunks, parentToDirectChildren);

        long parentSummaryCount = finalChunks.stream()
                .filter(c -> "parent_section_summary".equals(c.chunkType())).count();

        lastIngestMetrics.tallyFromChunks(finalChunks);

        log.info("[Chunk] processSections2 done: sections={} totalChunks={} " +
                "parentSummaries={} sectionSummaries={} tableSummaries={} normalizedRows={} text={} " +
                "detectedTables={} normalizedTables={} failedTables={} suppressedRawChars={} " +
                "suppressedLines={} tableLikeLinesDropped={} droppedLeakyTextChunks={} " +
                "rowsWithCellsJson={} rowsWithOnlyOneNonEmptyCell={} rowsWithEmptyCellsRatio={} " +
                "rowsWithGenericColumnKeys={} continuationRowsMerged={} multiRowHeadersMerged={} " +
                "crossPageHeaderCarryCount={} sparseRowsRepaired={} droppedCellFragments={} " +
                "headerSlotsCreated={} headerSlotsFallbackGeneric={} headerSiblingContaminationPrevented={} " +
                "headerAmbiguousFallbackCount={} avgHeaderTokenCountBefore={} avgHeaderTokenCountAfter={} " +
                "noisyComposedHeaderBeforeCount={} noisyComposedHeaderAfterCount={} " +
                "compactHeaderSuspiciousCount={} compactHeaderFallbackCount={} " +
                "spanAwareHeaderSelectedCount={} multiColumnHeaderRejectedCount={} " +
                "headerFragmentsWithCoordinates={} headerFragmentsWithoutCoordinates={} " +
                "valuesPreservedCount={} valuesDroppedCount={} rawTableModelsCreated={} " +
                "rawTableModelsCreatedFromSpreadsheet={} rawTableModelsCreatedFromBasic={} " +
                "rawTableCellsWithCoordinates={} rawTableCellsMissingCoordinates={} " +
                "structuredTablesNormalized={} markdownTablesNormalizedLegacy={} " +
                "pdfTablesUsingMarkdownBridge={} spreadsheetTablesUsingMarkdownBridge={} " +
                "basicTablesUsingMarkdownBridge={} pageAttributionPhysicalCount={}",
                sections.size(), finalChunks.size(), parentSummaryCount,
                finalChunks.stream().filter(c -> "section_summary".equals(c.chunkType())).count(),
                finalChunks.stream().filter(c -> "table_summary".equals(c.chunkType())).count(),
                finalChunks.stream().filter(c -> "normalized_table_row".equals(c.chunkType())).count(),
                finalChunks.stream().filter(c -> "text".equals(c.chunkType())).count(),
                lastIngestMetrics.getDetectedTables(),
                lastIngestMetrics.getNormalizedTables(),
                lastIngestMetrics.getFailedTables(),
                lastIngestMetrics.getSuppressedRawTableTextChars(),
                lastIngestMetrics.getSuppressedLines(),
                lastIngestMetrics.getTableLikeLinesDropped(),
                lastIngestMetrics.getDroppedLeakyTextChunks(),
                lastIngestMetrics.getRowsWithCellsJson(),
                lastIngestMetrics.getRowsWithOnlyOneNonEmptyCell(),
                String.format(Locale.ROOT, "%.3f", lastIngestMetrics.getRowsWithEmptyCellsRatio()),
                lastIngestMetrics.getRowsWithGenericColumnKeys(),
                lastIngestMetrics.getContinuationRowsMerged(),
                lastIngestMetrics.getMultiRowHeadersMerged(),
                lastIngestMetrics.getCrossPageHeaderCarryCount(),
                lastIngestMetrics.getSparseRowsRepaired(),
                lastIngestMetrics.getDroppedCellFragments(),
                lastIngestMetrics.getHeaderSlotsCreated(),
                lastIngestMetrics.getHeaderSlotsFallbackGeneric(),
                lastIngestMetrics.getHeaderSiblingContaminationPrevented(),
                lastIngestMetrics.getHeaderAmbiguousFallbackCount(),
                String.format(Locale.ROOT, "%.3f", lastIngestMetrics.getAvgHeaderTokenCountBefore()),
                String.format(Locale.ROOT, "%.3f", lastIngestMetrics.getAvgHeaderTokenCountAfter()),
                lastIngestMetrics.getNoisyComposedHeaderBeforeCount(),
                lastIngestMetrics.getNoisyComposedHeaderAfterCount(),
                lastIngestMetrics.getCompactHeaderSuspiciousCount(),
                lastIngestMetrics.getCompactHeaderFallbackCount(),
                lastIngestMetrics.getSpanAwareHeaderSelectedCount(),
                lastIngestMetrics.getMultiColumnHeaderRejectedCount(),
                lastIngestMetrics.getHeaderFragmentsWithCoordinates(),
                lastIngestMetrics.getHeaderFragmentsWithoutCoordinates(),
                lastIngestMetrics.getValuesPreservedCount(),
                lastIngestMetrics.getValuesDroppedCount(),
                lastIngestMetrics.getRawTableModelsCreated(),
                lastIngestMetrics.getRawTableModelsCreatedFromSpreadsheet(),
                lastIngestMetrics.getRawTableModelsCreatedFromBasic(),
                lastIngestMetrics.getRawTableCellsWithCoordinates(),
                lastIngestMetrics.getRawTableCellsMissingCoordinates(),
                lastIngestMetrics.getStructuredTablesNormalized(),
                lastIngestMetrics.getMarkdownTablesNormalizedLegacy(),
                lastIngestMetrics.getPdfTablesUsingMarkdownBridge(),
                lastIngestMetrics.getSpreadsheetTablesUsingMarkdownBridge(),
                lastIngestMetrics.getBasicTablesUsingMarkdownBridge(),
                lastIngestMetrics.getPageAttributionPhysicalCount());

        return finalChunks;
    }

    private NormalizedTableService.SuppressionProfile buildSectionSuppressionProfile(
            List<String> segments,
            Section section
    ) {
        NormalizedTableService.SuppressionProfile profile =
                NormalizedTableService.SuppressionProfile.empty();
        LogicalTableState state = null;
        int tableIndex = 0;
        for (String segment : segments) {
            if (segment.startsWith("[RAW_TABLE_REF:")) {
                RawTableBlock block = findRawTableBlock(section, segment);
                if (block != null && block.table() != null) {
                    profile = profile.merge(normalizedTableService.buildSuppressionProfile(List.of(block.table())));
                }
                continue;
            }
            if (!segment.startsWith("[TABLE_START]")) {
                continue;
            }
            String tableContent = segment
                    .replace("[TABLE_START]", "").replace("[TABLE_END]", "").trim();
            if (tableContent.isBlank()) {
                continue;
            }
            NormalizedTableService.NormalizationRequest request =
                    new NormalizedTableService.NormalizationRequest(
                            tableContent,
                            section.header(),
                            null,
                            section.startPage(),
                            section.endPage(),
                            tableIndex,
                            state);
            NormalizedTableService.NormalizationResult result = normalizedTableService.normalize(request);
            if (result.success()) {
                profile = profile.merge(normalizedTableService.buildSuppressionProfile(result));
                state = result.updatedState();
            }
            tableIndex++;
        }
        return profile;
    }

    private LogicalTableState processNormalizedTable(
            String tableMarkdown,
            Section section,
            String sectionId,
            String parentId,
            String tableId,
            String headingPathText,
            int sectionOrder,
            int headingLevel,
            int tableIndexInSection,
            LogicalTableState continuationState,
            List<DocumentChunk> finalChunks,
            int[] orderCountAndProfile
    ) {
        NormalizedTableService.NormalizationRequest request =
                new NormalizedTableService.NormalizationRequest(
                        tableMarkdown,
                        section.header(),
                        null,
                        section.startPage(),
                        section.endPage(),
                        tableIndexInSection,
                        continuationState);

        NormalizedTableService.NormalizationResult result = normalizedTableService.normalize(request);
        if (!result.success()) {
            lastIngestMetrics.incFailedTables();
            log.warn("[Chunk] Table normalization FAILED section='{}' tableId={} reason={}",
                    sectionId, tableId, result.failureReason());
            return continuationState;
        }

        lastIngestMetrics.incNormalizedTables();
        lastIngestMetrics.incMarkdownTablesNormalizedLegacy();
        lastIngestMetrics.addNormalizedRows(result.rows().size());
        lastIngestMetrics.addQualityStats(result.stats());

        finalChunks.add(createTableSummaryChunk(
                result.tableSummaryContent(),
                section,
                sectionId,
                parentId,
                tableId,
                headingPathText,
                orderCountAndProfile[0],
                sectionOrder,
                headingLevel,
                result.tableName()));
        orderCountAndProfile[0]++;
        orderCountAndProfile[1]++;
        lastIngestMetrics.incTableSummaries();

        for (NormalizedTableRow row : result.rows()) {
            finalChunks.add(createNormalizedRowChunk(
                    row,
                    section,
                    sectionId,
                    parentId,
                    tableId,
                    headingPathText,
                    orderCountAndProfile[0],
                    sectionOrder,
                    headingLevel));
            orderCountAndProfile[0]++;
            orderCountAndProfile[1]++;
            lastIngestMetrics.incNormalizedTableRowChunks();
        }

        return result.updatedState();
    }

    private LogicalTableState processNormalizedRawTable(
            RawTableModel rawTable,
            Section section,
            String sectionId,
            String parentId,
            String tableId,
            String headingPathText,
            int sectionOrder,
            int headingLevel,
            int tableIndexInSection,
            LogicalTableState continuationState,
            List<DocumentChunk> finalChunks,
            int[] orderCountAndProfile
    ) {
        NormalizedTableService.NormalizationRequest request =
                new NormalizedTableService.NormalizationRequest(
                        null,
                        section.header(),
                        null,
                        rawTable.pageNumber(),
                        rawTable.pageNumber(),
                        tableIndexInSection,
                        continuationState);

        NormalizedTableService.NormalizationResult result =
                normalizedTableService.normalizeRawTable(rawTable, request);
        if (!result.success()) {
            lastIngestMetrics.incFailedTables();
            log.warn("[Chunk] Raw table normalization FAILED section='{}' tableId={} reason={}",
                    sectionId, tableId, result.failureReason());
            return continuationState;
        }

        lastIngestMetrics.incNormalizedTables();
        lastIngestMetrics.incStructuredTablesNormalized();
        lastIngestMetrics.addNormalizedRows(result.rows().size());
        lastIngestMetrics.addQualityStats(result.stats());
        lastIngestMetrics.addRawTableCoordinateStats(rawTable);

        finalChunks.add(createTableSummaryChunk(
                result.tableSummaryContent(),
                section,
                sectionId,
                parentId,
                tableId,
                headingPathText,
                orderCountAndProfile[0],
                sectionOrder,
                headingLevel,
                result.tableName()));
        orderCountAndProfile[0]++;
        orderCountAndProfile[1]++;
        lastIngestMetrics.incTableSummaries();

        for (NormalizedTableRow row : result.rows()) {
            finalChunks.add(createNormalizedRowChunk(
                    row,
                    section,
                    sectionId,
                    parentId,
                    tableId,
                    headingPathText,
                    orderCountAndProfile[0],
                    sectionOrder,
                    headingLevel));
            orderCountAndProfile[0]++;
            orderCountAndProfile[1]++;
            lastIngestMetrics.incNormalizedTableRowChunks();
        }

        return result.updatedState();
    }

    private RawTableBlock findRawTableBlock(Section section, String segment) {
        Matcher matcher = RAW_TABLE_REF_PATTERN.matcher(segment.trim());
        if (!matcher.matches()) {
            return null;
        }
        String markerId = matcher.group(1);
        return section.rawTableBlocks().stream()
                .filter(block -> markerId.equals(block.markerId()))
                .findFirst()
                .orElse(null);
    }

    private String applySectionSuppression(String segment,
                                           NormalizedTableService.SuppressionProfile profile) {
        if (segment == null || segment.isBlank()) {
            return "";
        }
        NormalizedTableService.SuppressResult result =
                normalizedTableService.suppressRawTableText(segment, profile);
        lastIngestMetrics.addSuppressedRawTableTextChars(result.suppressedRawChars());
        lastIngestMetrics.addSuppressedLines(result.suppressedLines());
        lastIngestMetrics.addTableLikeLinesDropped(result.tableLikeLinesDropped());
        return result.text();
    }

    /** Drop markdown table lines left in text segments (raw table suppression). */
    private String stripResidualTableLines(String segment) {
        if (segment == null || segment.isBlank()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        long suppressed = 0;
        int lines = 0;
        for (String line : segment.split("\\R", -1)) {
            String t = line.trim();
            if (t.startsWith("|") && t.contains("|")) {
                suppressed += line.length();
                lines++;
                continue;
            }
            sb.append(line).append("\n");
        }
        lastIngestMetrics.addSuppressedRawTableTextChars(suppressed);
        lastIngestMetrics.addSuppressedLines(lines);
        return sb.toString().trim();
    }

    // ===================================================================
    // PARENT-CHILD HIERARCHY
    // ===================================================================

    /**
     * Xây dựng map: sectionId → danh sách Section con trực tiếp.
     *
     * "trực tiếp" = con ở level kế tiếp, không phải cháu.
     * Ví dụ: sec_1 → [1.1, 1.2], sec_1.1 → [1.1.1], KHÔNG bao gồm 1.1.1 trong sec_1.
     */
    public Map<String, List<Section>> buildParentChildMap(List<Section> sections) {
        // Thu thập tất cả section number đã biết
        Set<String> allNumbers = sections.stream()
                .map(s -> extractSectionNumber(s.header()))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<String, List<Section>> parentToChildren = new LinkedHashMap<>();

        for (Section section : sections) {
            String num = extractSectionNumber(section.header());
            if (num == null) continue;

            // Tìm direct parent number: "1.2.3" → direct parent là "1.2"
            int lastDot = num.lastIndexOf('.');
            if (lastDot <= 0) continue;

            String directParentNum = num.substring(0, lastDot);
            // Chỉ thêm vào map nếu parent tồn tại trong danh sách sections
            if (allNumbers.contains(directParentNum)) {
                String parentSectionId = "sec_" + directParentNum;
                parentToChildren.computeIfAbsent(parentSectionId, k -> new ArrayList<>()).add(section);
            }
        }

        return parentToChildren;
    }

    /** Tạo comma-separated string của childSectionIds từ danh sách Section con. */
    public String buildChildSectionIdsStr(List<Section> children) {
        return children.stream()
                .map(c -> extractSectionNumber(c.header()))
                .filter(Objects::nonNull)
                .map(num -> "sec_" + num)
                .collect(Collectors.joining(","));
    }

    /**
     * Tạo content cho parent_section_summary chunk.
     * Format:
     *   Section "X. Title" tổng hợp gồm các mục con:
     *   - [sec_X.1] X.1 Tiêu đề subsection [preview đầu ~150 ký tự nếu có]
     *   - [sec_X.2] X.2 Tiêu đề subsection
     *   ...
     *   [Nội dung trực tiếp của section cha nếu có:]
     *   ...
     */
    private String buildParentSectionSummaryContent(Section parent,
                                                     List<Section> children,
                                                     String childSectionIds) {
        StringBuilder sb = new StringBuilder();
        sb.append("Mục \"").append(safeText(parent.header(), "Untitled")).append("\" tổng hợp gồm các mục con:\n\n");

        for (Section child : children) {
            String childNum = extractSectionNumber(child.header());
            String childId = childNum != null ? "sec_" + childNum : "?";
            sb.append("- [").append(childId).append("] ").append(safeText(child.header(), "")).append("\n");

            // Preview đầu nội dung của section con (nếu có)
            if (child.content() != null && !child.content().isBlank()) {
                String preview = child.content().replaceAll("\\[TABLE_START].*?\\[TABLE_END]", "[bảng]")
                        .replaceAll("\\s+", " ").trim();
                if (preview.length() > 300) {
                    int cutAt = preview.lastIndexOf(' ', 300);
                    cutAt = (cutAt >= 50) ? cutAt : 300;
                    preview = preview.substring(0, cutAt) + "…";
                }
                sb.append("  ").append(preview).append("\n");
            }
        }

        // Nội dung trực tiếp của section cha (nếu không rỗng)
        if (parent.content() != null && !parent.content().isBlank()) {
            String parentContent = parent.content().trim();
            if (parentContent.length() > 800) {
                int cutAt = parentContent.lastIndexOf(' ', 800);
                cutAt = (cutAt >= 50) ? cutAt : 800;
                parentContent = parentContent.substring(0, cutAt) + "…";
            }
            sb.append("\nNội dung trực tiếp:\n").append(parentContent).append("\n");
        }

        sb.append("\nchild_section_ids: ").append(childSectionIds);

        return sb.toString().trim();
    }

    // ===================================================================
    // PSEUDO-TABLE DETECTION (fallback khi parser không detect được bảng)
    // ===================================================================

    /**
     * Phát hiện và chuyển đổi text có dạng bảng (space/tab-delimited) thành Markdown table.
     * Áp dụng khi Tabula không detect được bảng (tables=0) nhưng text có cấu trúc dạng bảng.
     *
     * Pattern nhận biết pseudo-table:
     * - 3+ dòng liên tiếp mà mỗi dòng có 3+ cột khi split bởi 2+ spaces
     * - Các dòng có số cột tương đồng (±1 so với dòng đầu)
     * - Hoặc: 3+ dòng có tab character rõ ràng
     */
    public String detectAndConvertTextTables(String text) {
        if (text == null || text.isBlank()) return text;

        String[] lines = text.split("\\R", -1);
        StringBuilder result = new StringBuilder();
        List<String> candidateBlock = new ArrayList<>();

        for (String line : lines) {
            if (isPseudoTableLine(line)) {
                candidateBlock.add(line);
            } else {
                if (candidateBlock.size() >= PSEUDO_TABLE_MIN_ROWS) {
                    String markdown = convertLinesToMarkdownTable(candidateBlock);
                    if (markdown != null) {
                        result.append("\n[TABLE_START]\n").append(markdown).append("[TABLE_END]\n");
                        log.info("[Chunk] Pseudo-table CONVERTED: {} lines → Markdown table " +
                                "(cols={}, consistency check passed)",
                                candidateBlock.size(),
                                candidateBlock.get(0).trim().split("\\s{2,}|\t").length);
                    } else {
                        log.info("[Chunk] Pseudo-table REJECTED: {} candidate lines → suppressed (no fallback text).",
                                candidateBlock.size());
                        lastIngestMetrics.addSuppressedRawTableTextChars(
                                candidateBlock.stream().mapToInt(String::length).sum());
                    }
                } else {
                    for (String tl : candidateBlock) result.append(tl).append("\n");
                }
                candidateBlock.clear();
                result.append(line).append("\n");
            }
        }

        // Flush remaining candidate block
        if (candidateBlock.size() >= PSEUDO_TABLE_MIN_ROWS) {
            String markdown = convertLinesToMarkdownTable(candidateBlock);
            if (markdown != null) {
                result.append("\n[TABLE_START]\n").append(markdown).append("[TABLE_END]\n");
                log.info("[Chunk] Pseudo-table CONVERTED (end of text): {} lines → Markdown table",
                        candidateBlock.size());
            } else {
                log.info("[Chunk] Pseudo-table REJECTED (end of text): {} candidate lines → suppressed.",
                        candidateBlock.size());
                lastIngestMetrics.addSuppressedRawTableTextChars(
                        candidateBlock.stream().mapToInt(String::length).sum());
            }
        } else {
            for (String tl : candidateBlock) result.append(tl).append("\n");
        }

        return result.toString();
    }

    /**
     * Kiểm tra dòng có dạng bảng không:
     * - Có 3+ cột khi split bởi 2+ whitespace, HOẶC
     * - Có tab character và ít nhất 2+ cột
     */
    private boolean isPseudoTableLine(String line) {
        if (line == null || line.trim().length() < 8) return false;
        String trimmed = line.trim();

        // Bỏ qua: dòng là markdown bảng (đã xử lý bởi splitByTableBlocks)
        if (trimmed.startsWith("|")) return false;

        // Bỏ qua: dòng là heading RÕ RÀNG — có dấu chấm sau số ("1. Title" hoặc "1.2 Title")
        // KHÔNG bỏ qua dòng "1    data" (số không có chấm, chỉ có khoảng trắng) vì đó là table row
        if (trimmed.matches("^\\d+(?:\\.\\d+)*\\.\\s+.{3,}")) return false;  // "1. Title", "1.2. Sub"
        if (trimmed.matches("^\\d+\\.\\d+\\s+.{3,}")) return false;          // "1.2 Title"

        // Bỏ qua: dòng là bullet/list
        if (trimmed.startsWith("-") || trimmed.startsWith("•") || trimmed.startsWith("*")) return false;

        // Bỏ qua: dòng quá ngắn hoặc là dòng trống
        if (trimmed.length() < 8) return false;

        // Tab-separated
        if (trimmed.contains("\t")) {
            return trimmed.split("\t").length >= 2;
        }

        // Space-aligned: 2+ consecutive spaces là separator giữa các cột
        // "STT  Tên chức năng  Mô tả" → ["STT", "Tên chức năng", "Mô tả"] → 3 phần → table line
        String[] parts = trimmed.split("\\s{2,}");
        if (parts.length < PSEUDO_TABLE_MIN_COLS) return false;
        // Tất cả parts phải không rỗng
        return Arrays.stream(parts).allMatch(p -> p != null && !p.isBlank());
    }

    /**
     * Chuyển danh sách dòng text sang Markdown table.
     * Dòng đầu tiên là header, các dòng còn lại là data.
     * Trả về null nếu không đủ nhất quán để tạo bảng hợp lệ.
     */
    private String convertLinesToMarkdownTable(List<String> lines) {
        // Xác định separator: tab hoặc 2+ spaces
        boolean useTab = lines.get(0).contains("\t");
        String splitRegex = useTab ? "\t" : "\\s{2,}";

        List<String[]> rows = lines.stream()
                .map(l -> l.trim().split(splitRegex, -1))
                .filter(cols -> cols.length >= 2)
                .toList();

        if (rows.isEmpty()) return null;

        int headerCols = rows.get(0).length;

        // Kiểm tra nhất quán: ≥70% dòng có số cột trong khoảng [headerCols-1, headerCols+1]
        long consistentCount = rows.stream()
                .filter(r -> Math.abs(r.length - headerCols) <= 1)
                .count();
        if (consistentCount < rows.size() * 0.7) return null;

        String[] header = rows.get(0);
        StringBuilder sb = new StringBuilder();

        // Header row
        sb.append("| ");
        for (String col : header) sb.append(col.trim()).append(" | ");
        sb.append("\n| ");
        for (int i = 0; i < header.length; i++) sb.append("--- | ");
        sb.append("\n");

        // Data rows
        for (int i = 1; i < rows.size(); i++) {
            String[] row = rows.get(i);
            sb.append("| ");
            for (int j = 0; j < headerCols; j++) {
                String cell = j < row.length ? row[j].trim() : "";
                sb.append(cell).append(" | ");
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    // ===================================================================
    // POST-CHUNKING VALIDATION
    // ===================================================================

    /**
     * Validation sau khi tạo xong toàn bộ chunks.
     *
     * Kiểm tra:
     * 1. Số parent_section_summary = số parent sections
     * 2. Mọi parent_section_summary có child_section_ids hợp lệ
     * 3. Không có chunk có content rỗng
     * 4. parent_section_summary chunk count per sectionId là đúng 1
     */
    private void validateChunks(List<DocumentChunk> chunks,
                                  Map<String, List<Section>> parentToDirectChildren) {
        int expectedParentSummaries = parentToDirectChildren.size();
        long actualParentSummaries = chunks.stream()
                .filter(c -> "parent_section_summary".equals(c.chunkType()))
                .count();

        if (actualParentSummaries != expectedParentSummaries) {
            log.warn("[Chunk][Validation] FAIL: expected {} parent_section_summary chunks, got {}. " +
                    "Parent sections: {}", expectedParentSummaries, actualParentSummaries,
                    parentToDirectChildren.keySet());
        } else {
            log.info("[Chunk][Validation] parent_section_summary: OK ({}/{})",
                    actualParentSummaries, expectedParentSummaries);
        }

        // parent_section_summary phải có childSectionIds
        chunks.stream()
                .filter(c -> "parent_section_summary".equals(c.chunkType()))
                .filter(c -> c.childSectionIds() == null || c.childSectionIds().isBlank())
                .forEach(c -> log.warn("[Chunk][Validation] parent_section_summary '{}' thiếu childSectionIds!",
                        c.sectionId()));

        // Không có chunk content rỗng
        long blankCount = chunks.stream()
                .filter(c -> c.content() == null || c.content().isBlank())
                .count();
        if (blankCount > 0) {
            log.warn("[Chunk][Validation] {} chunks có content rỗng — sẽ không được index!", blankCount);
        }

        // Đếm theo loại để dễ debug
        Map<String, Long> byType = chunks.stream()
                .collect(Collectors.groupingBy(c -> safeText(c.chunkType(), "unknown"), Collectors.counting()));
        log.info("[Chunk][Validation] Chunk distribution: {}", byType);

        // Kiểm tra retrieval test: mỗi parent_section_summary phải có childSectionIds
        // trỏ đến ít nhất 1 sectionId tồn tại trong các chunk khác
        Set<String> allSectionIds = chunks.stream()
                .map(DocumentChunk::sectionId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        chunks.stream()
                .filter(c -> "parent_section_summary".equals(c.chunkType()))
                .filter(c -> c.childSectionIds() != null && !c.childSectionIds().isBlank())
                .forEach(parent -> {
                    String[] childIds = parent.childSectionIds().split(",");
                    long missingChildren = Arrays.stream(childIds)
                            .map(String::trim)
                            .filter(id -> !id.isBlank())
                            .filter(id -> !allSectionIds.contains(id))
                            .count();
                    if (missingChildren > 0) {
                        log.warn("[Chunk][Validation] parent_section_summary '{}' có {} child sectionId " +
                                "không có chunk tương ứng!", parent.sectionId(), missingChildren);
                    }
                });
    }

    // ===================================================================
    // TABLE & TEXT SPLITTING
    // ===================================================================

    private List<String> splitByTableBlocks(String content) {
        List<String> result = new ArrayList<>();
        if (content == null || content.isBlank()) return result;

        if (content.contains("[TABLE_START]") || content.contains("[RAW_TABLE_REF:")) {
            return splitByExplicitTableMarkers(content);
        }

        String[] lines = content.split("\\R");
        StringBuilder normalText = new StringBuilder();
        StringBuilder tableText = new StringBuilder();
        boolean inTable = false;

        for (String line : lines) {
            String trimmed = line.trim();
            boolean tableLine = trimmed.startsWith("|") && trimmed.endsWith("|");

            if (tableLine) {
                if (!inTable) {
                    if (normalText.length() > 0) {
                        result.add(normalText.toString().trim());
                        normalText.setLength(0);
                    }
                    inTable = true;
                }
                tableText.append(line).append("\n");
            } else {
                if (inTable) {
                    result.add("[TABLE_START]\n" + tableText.toString().trim() + "\n[TABLE_END]");
                    tableText.setLength(0);
                    inTable = false;
                }
                normalText.append(line).append("\n");
            }
        }

        if (inTable && tableText.length() > 0) {
            result.add("[TABLE_START]\n" + tableText.toString().trim() + "\n[TABLE_END]");
        }
        if (normalText.length() > 0) {
            result.add(normalText.toString().trim());
        }
        return result;
    }

    private List<String> splitByExplicitTableMarkers(String content) {
        List<String> result = new ArrayList<>();
        int searchFrom = 0;
        while (searchFrom < content.length()) {
            int tableStart = content.indexOf("[TABLE_START]", searchFrom);
            int rawStart = content.indexOf("[RAW_TABLE_REF:", searchFrom);
            if (tableStart < 0 || (rawStart >= 0 && rawStart < tableStart)) {
                if (rawStart < 0) {
                    addIfNotBlank(result, content.substring(searchFrom));
                    break;
                }
                addIfNotBlank(result, content.substring(searchFrom, rawStart));
                int rawEnd = content.indexOf("]", rawStart);
                if (rawEnd < 0) {
                    addIfNotBlank(result, content.substring(rawStart));
                    break;
                }
                addIfNotBlank(result, content.substring(rawStart, rawEnd + 1));
                searchFrom = rawEnd + 1;
                continue;
            }
            addIfNotBlank(result, content.substring(searchFrom, tableStart));

            int tableContentStart = tableStart + "[TABLE_START]".length();
            int tableEnd = content.indexOf("[TABLE_END]", tableContentStart);
            if (tableEnd < 0) { addIfNotBlank(result, content.substring(tableContentStart)); break; }

            String tableContent = content.substring(tableContentStart, tableEnd).trim();
            if (!tableContent.isBlank()) result.add("[TABLE_START]\n" + tableContent + "\n[TABLE_END]");
            searchFrom = tableEnd + "[TABLE_END]".length();
        }
        return result;
    }

    private void addIfNotBlank(List<String> result, String value) {
        if (value == null) return;
        String v = value.trim();
        if (!v.isBlank()) result.add(v);
    }

    // ===================================================================
    // TEXT CHUNKING
    // ===================================================================

    private List<String> chunkPlainText(String text) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isBlank()) return chunks;

        String normalized = text.trim();
        if (normalized.length() <= MAX_CHARS_PER_TEXT_CHUNK) {
            chunks.add(normalized);
            return chunks;
        }

        List<int[]> paragraphBounds = findParagraphBounds(normalized);
        if (paragraphBounds.size() > 1) return chunkByParagraphs(normalized, paragraphBounds);

        int start = 0;
        while (start < normalized.length()) {
            int end = Math.min(start + MAX_CHARS_PER_TEXT_CHUNK, normalized.length());
            if (end < normalized.length()) {
                int newlineBoundary = normalized.lastIndexOf("\n", end);
                int sentenceBoundary = findLastSentenceBoundary(normalized, start, end);
                int boundary = Math.max(newlineBoundary, sentenceBoundary);
                if (boundary > start + 500) end = boundary + 1;
            }
            String chunk = normalized.substring(start, end).trim();
            if (!chunk.isBlank()) chunks.add(chunk);
            if (end >= normalized.length()) break;
            start = Math.max(0, end - OVERLAP_CHARS);
        }
        return chunks;
    }

    private List<int[]> findParagraphBounds(String text) {
        List<int[]> bounds = new ArrayList<>();
        String[] lines = text.split("\\R");
        int charPos = 0, paragraphStart = 0;
        for (String line : lines) {
            if (line.isBlank()) {
                if (charPos > paragraphStart) bounds.add(new int[]{paragraphStart, charPos});
                paragraphStart = charPos + line.length() + 1;
            }
            charPos += line.length() + 1;
        }
        if (paragraphStart < text.length()) bounds.add(new int[]{paragraphStart, text.length()});
        return bounds;
    }

    private List<String> chunkByParagraphs(String text, List<int[]> paragraphBounds) {
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (int[] bound : paragraphBounds) {
            String para = text.substring(bound[0], Math.min(bound[1], text.length())).trim();
            if (para.isBlank()) continue;
            if (current.length() + para.length() > MAX_CHARS_PER_TEXT_CHUNK && current.length() > 0) {
                String chunk = current.toString().trim();
                if (!chunk.isBlank()) chunks.add(chunk);
                String overlap = chunk.length() > OVERLAP_CHARS
                        ? chunk.substring(chunk.length() - OVERLAP_CHARS) : chunk;
                current.setLength(0);
                current.append(overlap).append("\n\n");
            }
            current.append(para).append("\n\n");
        }
        if (!current.toString().isBlank()) chunks.add(current.toString().trim());
        return chunks;
    }

    private int findLastSentenceBoundary(String text, int start, int end) {
        for (int i = end; i > start; i--) {
            char c = text.charAt(i - 1);
            if (c == '.' || c == '!' || c == '?' || c == '\n') return i;
        }
        return end;
    }

    // ===================================================================
    // CHUNK FACTORIES
    // ===================================================================

    /** Tạo chunk thông thường (không có childSectionIds). */
    private DocumentChunk createEnrichedChunk(
            String rawContent, Section section, String chunkType,
            String sectionId, String parentId, String tableId, String headingPathText,
            int orderIndex, int sectionOrder, int headingLevel) {
        return createChunkWithChildren(rawContent, section, chunkType,
                sectionId, parentId, tableId, headingPathText,
                orderIndex, sectionOrder, headingLevel, null);
    }

    /** Tạo chunk với childSectionIds (dùng cho parent_section_summary). */
    private DocumentChunk createChunkWithChildren(
            String rawContent, Section section, String chunkType,
            String sectionId, String parentId, String tableId, String headingPathText,
            int orderIndex, int sectionOrder, int headingLevel, String childSectionIds) {
        return createChunkWithChildren(
                rawContent, section, chunkType, sectionId, parentId, tableId, headingPathText,
                orderIndex, sectionOrder, headingLevel, childSectionIds,
                null, null, null, null, null);
    }

    private DocumentChunk createChunkWithChildren(
            String rawContent, Section section, String chunkType,
            String sectionId, String parentId, String tableId, String headingPathText,
            int orderIndex, int sectionOrder, int headingLevel, String childSectionIds,
            String tableName, Integer rowIndex, String cellsJson, String groupContext, Integer rowPageStart) {
        String content = safeText(rawContent, "").trim();
        String safeHeading = safeText(headingPathText, safeText(section.header(), "Untitled Section"));
        int tokenEstimate = Math.max(1, content.length() / 4);

        int effectiveStart = section.startPage();
        int effectiveEnd = section.endPage();
        if (rowPageStart != null && rowPageStart > 0) {
            effectiveStart = rowPageStart;
            effectiveEnd = rowPageStart;
        }

        return new DocumentChunk(
                content,
                safeText(section.header(), "Untitled Section"),
                effectiveStart,
                effectiveEnd,
                chunkType,
                sectionId,
                parentId,
                tableId,
                safeHeading,
                orderIndex,
                tokenEstimate,
                sectionOrder,
                headingLevel,
                childSectionIds,
                tableName,
                rowIndex,
                cellsJson,
                groupContext,
                rowPageStart
        );
    }

    private DocumentChunk createTableSummaryChunk(
            String summaryContent,
            Section section,
            String sectionId,
            String parentId,
            String tableId,
            String headingPathText,
            int orderIndex,
            int sectionOrder,
            int headingLevel,
            String tableName
    ) {
        return createChunkWithChildren(
                summaryContent, section, "table_summary",
                sectionId, parentId, tableId, headingPathText,
                orderIndex, sectionOrder, headingLevel, null,
                tableName, null, null, null, null);
    }

    private DocumentChunk createNormalizedRowChunk(
            NormalizedTableRow row,
            Section section,
            String sectionId,
            String parentId,
            String tableId,
            String headingPathText,
            int orderIndex,
            int sectionOrder,
            int headingLevel
    ) {
        return createChunkWithChildren(
                row.canonicalText(), section, "normalized_table_row",
                sectionId, parentId, tableId, headingPathText,
                orderIndex, sectionOrder, headingLevel, null,
                row.tableName(), row.rowIndex(), row.cellsJson(), row.groupContext(), row.pageStart());
    }

    // ===================================================================
    // SUMMARY BUILDERS
    // ===================================================================

    private String buildSectionSummary(String sectionTitle, List<String> textSegments) {
        StringBuilder sb = new StringBuilder();
        sb.append("Tổng quan section \"").append(safeText(sectionTitle, "Untitled Section")).append("\":\n\n");
        int totalAdded = 0, maxSummaryChars = 1800;
        for (String seg : textSegments) {
            if (totalAdded >= maxSummaryChars) break;
            String trimmed = seg.trim();
            if (trimmed.isBlank()) continue;
            int take = Math.min(trimmed.length(), maxSummaryChars - totalAdded);
            sb.append(trimmed, 0, take);
            if (take < trimmed.length()) sb.append("…");
            sb.append("\n\n");
            totalAdded += take;
        }
        return sb.toString().trim();
    }

    // ===================================================================
    // HEADING PATH UTILITIES
    // ===================================================================

    private String buildHeadingPathText(List<HeadingNode> stack, String header,
                                         String sectionNumber, int sectionIndex) {
        String safeHeader = safeText(header, "Untitled Section");
        if (sectionNumber == null || sectionNumber.isBlank()) return safeHeader;

        int level = sectionNumber.split("\\.").length;
        String titleOnly = extractTitleOnly(safeHeader, sectionNumber);
        while (stack.size() >= level) stack.remove(stack.size() - 1);
        stack.add(new HeadingNode(sectionNumber, titleOnly));

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < stack.size(); i++) {
            HeadingNode node = stack.get(i);
            if (i > 0) sb.append(" > ");
            sb.append(node.number()).append(" ").append(node.title());
        }
        return sb.toString();
    }

    private String extractTitleOnly(String header, String sectionNumber) {
        if (header == null) return "";
        String h = header.trim();
        if (h.startsWith(sectionNumber)) h = h.substring(sectionNumber.length()).trim();
        if (h.startsWith(".")) h = h.substring(1).trim();
        return safeText(h, header).trim();
    }

    public String extractSectionNumber(String header) {
        if (header == null || header.isBlank()) return null;
        Matcher m = SECTION_NUMBER_PATTERN.matcher(header.trim());
        return m.find() ? m.group(1) : null;
    }

    private String extractParentSectionNumber(String sectionNumber) {
        if (sectionNumber == null || sectionNumber.isBlank()) return "root";
        int lastDot = sectionNumber.lastIndexOf('.');
        return lastDot > 0 ? sectionNumber.substring(0, lastDot) : sectionNumber;
    }

    /**
     * Kiểm tra nhanh xem content có cấu trúc dạng bảng không —
     * dùng khi pseudo-table detection không convert được nhưng content vẫn có dấu hiệu bảng.
     *
     * Tiêu chí: ≥ 30% dòng không trống có ≥ 2 cột khi split bởi 2+ spaces hoặc tab.
     */
    public boolean isLikelyTableLikeContent(String text) {
        if (text == null || text.isBlank()) return false;
        String[] lines = text.split("\\R", -1);
        int nonBlankLines = 0;
        int tableLikeLines = 0;
        for (String line : lines) {
            if (line.isBlank()) continue;
            nonBlankLines++;
            String trimmed = line.trim();
            if (trimmed.startsWith("|")) { tableLikeLines++; continue; } // markdown table
            if (trimmed.contains("\t") && trimmed.split("\t").length >= 2) { tableLikeLines++; continue; }
            if (trimmed.split("\\s{2,}").length >= 2) tableLikeLines++;
        }
        return nonBlankLines >= 3 && tableLikeLines >= nonBlankLines * 0.30;
    }

    private String safeText(String value, String defaultValue) {
        return (value == null || value.isBlank()) ? defaultValue : value;
    }

    private record HeadingNode(String number, String title) {}
}
