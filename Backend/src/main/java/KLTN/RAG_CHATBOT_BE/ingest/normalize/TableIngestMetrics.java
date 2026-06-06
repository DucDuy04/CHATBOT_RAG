package KLTN.RAG_CHATBOT_BE.ingest.normalize;

import KLTN.RAG_CHATBOT_BE.ingest.parser.RawTableCell;
import KLTN.RAG_CHATBOT_BE.ingest.parser.RawTableModel;
import KLTN.RAG_CHATBOT_BE.ingest.parser.RawTableRow;
import lombok.Getter;

/**
 * Metrics for normalized table ingest (task 23J).
 */
@Getter
public class TableIngestMetrics {

    private int detectedTables;
    private int normalizedTables;
    private int failedTables;
    private int normalizedRows;
    private int tableSummaries;
    private long suppressedRawTableTextChars;
    private int suppressedLines;
    private int tableLikeLinesDropped;
    private int droppedLeakyTextChunks;
    private int textChunks;
    private int normalizedTableRowChunks;
    private int tableSummaryChunks;
    private int qdrantPoints;
    private int rowsWithCellsJson;
    private int rowsWithOnlyOneNonEmptyCell;
    private double rowsWithEmptyCellsRatio;
    private int rowsWithGenericColumnKeys;
    private int continuationRowsMerged;
    private int multiRowHeadersMerged;
    private int crossPageHeaderCarryCount;
    private int sparseRowsRepaired;
    private int droppedCellFragments;
    private int headerSlotsCreated;
    private int headerSlotsFallbackGeneric;
    private int headerSiblingContaminationPrevented;
    private int headerAmbiguousFallbackCount;
    private double avgHeaderTokenCountBefore;
    private double avgHeaderTokenCountAfter;
    private int noisyComposedHeaderBeforeCount;
    private int noisyComposedHeaderAfterCount;
    private int compactHeaderSuspiciousCount;
    private int compactHeaderFallbackCount;
    private int spanAwareHeaderSelectedCount;
    private int multiColumnHeaderRejectedCount;
    private int headerFragmentsWithCoordinates;
    private int headerFragmentsWithoutCoordinates;
    private int valuesPreservedCount;
    private int valuesDroppedCount;
    private int rawTableModelsCreated;
    private int rawTableModelsCreatedFromSpreadsheet;
    private int rawTableModelsCreatedFromBasic;
    private int rawTableCellsWithCoordinates;
    private int rawTableCellsMissingCoordinates;
    private int structuredTablesNormalized;
    private int markdownTablesNormalizedLegacy;
    private int pdfTablesUsingMarkdownBridge;
    private int spreadsheetTablesUsingMarkdownBridge;
    private int basicTablesUsingMarkdownBridge;
    private int coordinateHeaderSlotsCreated;
    private int coordinateHeaderFallbackColCount;
    private int coordinateRowMergeAttempts;
    private int coordinateRowMergesAccepted;
    private int coordinateRowMergesRejectedByNewIdentifier;
    private int coordinateRowMergesRejectedByNewRowNumber;
    private int coordinateRowMergesRejectedByXOverlap;
    private int malformedMultiIdentifierRowsCount;
    private int pageAttributionPhysicalCount;
    private int zeroOverlapHeaderRejected;
    private int broadSpanningHeaderDemoted;
    private int collisionSuffixPrevented;

    // DOCX-specific metrics
    private int docxFilesParsed;
    private int docxParagraphsExtracted;
    private int docxTablesDetected;
    private int docxTablesNormalized;
    private int docxTableRowsNormalized;
    private int docxCellsExtracted;
    private int docxMergedCellsDetected;
    private int docxHorizontalSpansDetected;
    private int docxVerticalMergesDetected;
    private int docxTablesUsingMarkdownBridge;
    private int docxTablesUsingRawTableModel;
    private int groupContextPopulatedCount;

    public void reset() {
        detectedTables = 0;
        normalizedTables = 0;
        failedTables = 0;
        normalizedRows = 0;
        tableSummaries = 0;
        suppressedRawTableTextChars = 0;
        suppressedLines = 0;
        tableLikeLinesDropped = 0;
        droppedLeakyTextChunks = 0;
        textChunks = 0;
        normalizedTableRowChunks = 0;
        tableSummaryChunks = 0;
        qdrantPoints = 0;
        rowsWithCellsJson = 0;
        rowsWithOnlyOneNonEmptyCell = 0;
        rowsWithEmptyCellsRatio = 0.0;
        rowsWithGenericColumnKeys = 0;
        continuationRowsMerged = 0;
        multiRowHeadersMerged = 0;
        crossPageHeaderCarryCount = 0;
        sparseRowsRepaired = 0;
        droppedCellFragments = 0;
        headerSlotsCreated = 0;
        headerSlotsFallbackGeneric = 0;
        headerSiblingContaminationPrevented = 0;
        headerAmbiguousFallbackCount = 0;
        avgHeaderTokenCountBefore = 0.0;
        avgHeaderTokenCountAfter = 0.0;
        noisyComposedHeaderBeforeCount = 0;
        noisyComposedHeaderAfterCount = 0;
        compactHeaderSuspiciousCount = 0;
        compactHeaderFallbackCount = 0;
        spanAwareHeaderSelectedCount = 0;
        multiColumnHeaderRejectedCount = 0;
        headerFragmentsWithCoordinates = 0;
        headerFragmentsWithoutCoordinates = 0;
        valuesPreservedCount = 0;
        valuesDroppedCount = 0;
        rawTableModelsCreated = 0;
        rawTableModelsCreatedFromSpreadsheet = 0;
        rawTableModelsCreatedFromBasic = 0;
        rawTableCellsWithCoordinates = 0;
        rawTableCellsMissingCoordinates = 0;
        structuredTablesNormalized = 0;
        markdownTablesNormalizedLegacy = 0;
        pdfTablesUsingMarkdownBridge = 0;
        spreadsheetTablesUsingMarkdownBridge = 0;
        basicTablesUsingMarkdownBridge = 0;
        coordinateHeaderSlotsCreated = 0;
        coordinateHeaderFallbackColCount = 0;
        coordinateRowMergeAttempts = 0;
        coordinateRowMergesAccepted = 0;
        coordinateRowMergesRejectedByNewIdentifier = 0;
        coordinateRowMergesRejectedByNewRowNumber = 0;
        coordinateRowMergesRejectedByXOverlap = 0;
        malformedMultiIdentifierRowsCount = 0;
        pageAttributionPhysicalCount = 0;
        zeroOverlapHeaderRejected = 0;
        broadSpanningHeaderDemoted = 0;
        collisionSuffixPrevented = 0;
        docxFilesParsed = 0;
        docxParagraphsExtracted = 0;
        docxTablesDetected = 0;
        docxTablesNormalized = 0;
        docxTableRowsNormalized = 0;
        docxCellsExtracted = 0;
        docxMergedCellsDetected = 0;
        docxHorizontalSpansDetected = 0;
        docxVerticalMergesDetected = 0;
        docxTablesUsingMarkdownBridge = 0;
        docxTablesUsingRawTableModel = 0;
        groupContextPopulatedCount = 0;
    }

    public void incDetectedTables() {
        detectedTables++;
    }

    public void incNormalizedTables() {
        normalizedTables++;
    }

    public void incFailedTables() {
        failedTables++;
    }

    public void addNormalizedRows(int count) {
        normalizedRows += count;
    }

    public void incTableSummaries() {
        tableSummaries++;
    }

    public void addSuppressedRawTableTextChars(long chars) {
        suppressedRawTableTextChars += chars;
    }

    public void addSuppressedLines(int lines) {
        suppressedLines += lines;
    }

    public void addTableLikeLinesDropped(int lines) {
        tableLikeLinesDropped += lines;
    }

    public void incDroppedLeakyTextChunks() {
        droppedLeakyTextChunks++;
    }

    public void incTextChunks() {
        textChunks++;
    }

    public void incNormalizedTableRowChunks() {
        normalizedTableRowChunks++;
    }

    public void incTableSummaryChunks() {
        tableSummaryChunks++;
    }

    public void setQdrantPoints(int count) {
        qdrantPoints = count;
    }

    public void addQualityStats(NormalizedTableService.QualityStats stats) {
        if (stats == null) {
            return;
        }
        int previousRows = rowsWithCellsJson;
        rowsWithCellsJson += stats.rowsWithCellsJson();
        rowsWithOnlyOneNonEmptyCell += stats.rowsWithOnlyOneNonEmptyCell();
        if (rowsWithCellsJson > 0) {
            rowsWithEmptyCellsRatio =
                    ((rowsWithEmptyCellsRatio * previousRows)
                            + (stats.rowsWithEmptyCellsRatio() * stats.rowsWithCellsJson()))
                            / rowsWithCellsJson;
        }
        rowsWithGenericColumnKeys += stats.rowsWithGenericColumnKeys();
        continuationRowsMerged += stats.continuationRowsMerged();
        multiRowHeadersMerged += stats.multiRowHeadersMerged();
        crossPageHeaderCarryCount += stats.crossPageHeaderCarryCount();
        sparseRowsRepaired += stats.sparseRowsRepaired();
        droppedCellFragments += stats.droppedCellFragments();
        int previousSlots = headerSlotsCreated;
        headerSlotsCreated += stats.headerSlotsCreated();
        headerSlotsFallbackGeneric += stats.headerSlotsFallbackGeneric();
        headerSiblingContaminationPrevented += stats.headerSiblingContaminationPrevented();
        headerAmbiguousFallbackCount += stats.headerAmbiguousFallbackCount();
        if (headerSlotsCreated > 0) {
            avgHeaderTokenCountBefore =
                    ((avgHeaderTokenCountBefore * previousSlots)
                            + (stats.avgHeaderTokenCountBefore() * stats.headerSlotsCreated()))
                            / headerSlotsCreated;
            avgHeaderTokenCountAfter =
                    ((avgHeaderTokenCountAfter * previousSlots)
                            + (stats.avgHeaderTokenCountAfter() * stats.headerSlotsCreated()))
                            / headerSlotsCreated;
        }
        noisyComposedHeaderBeforeCount += stats.noisyComposedHeaderBeforeCount();
        noisyComposedHeaderAfterCount += stats.noisyComposedHeaderAfterCount();
        compactHeaderSuspiciousCount += stats.compactHeaderSuspiciousCount();
        compactHeaderFallbackCount += stats.compactHeaderFallbackCount();
        spanAwareHeaderSelectedCount += stats.spanAwareHeaderSelectedCount();
        multiColumnHeaderRejectedCount += stats.multiColumnHeaderRejectedCount();
        headerFragmentsWithCoordinates += stats.headerFragmentsWithCoordinates();
        headerFragmentsWithoutCoordinates += stats.headerFragmentsWithoutCoordinates();
        valuesPreservedCount += stats.valuesPreservedCount();
        valuesDroppedCount += stats.valuesDroppedCount();
        zeroOverlapHeaderRejected += stats.zeroOverlapHeaderRejected();
        broadSpanningHeaderDemoted += stats.broadSpanningHeaderDemoted();
        collisionSuffixPrevented += stats.collisionSuffixPrevented();
    }

    public void incRawTableModelsCreated(RawTableModel.ExtractorType extractorType) {
        rawTableModelsCreated++;
        if (extractorType == RawTableModel.ExtractorType.BASIC) {
            rawTableModelsCreatedFromBasic++;
        } else if (extractorType == RawTableModel.ExtractorType.SPREADSHEET) {
            rawTableModelsCreatedFromSpreadsheet++;
        }
    }

    // ---- DOCX increment helpers ----
    public void incDocxFilesParsed()               { docxFilesParsed++; }
    public void addDocxParagraphsExtracted(int n)  { docxParagraphsExtracted += n; }
    public void incDocxTablesDetected()            { docxTablesDetected++; }
    public void incDocxTablesNormalized()          { docxTablesNormalized++; }
    public void addDocxTableRowsNormalized(int n)  { docxTableRowsNormalized += n; }
    public void addDocxCellsExtracted(int n)       { docxCellsExtracted += n; }
    public void incDocxMergedCellsDetected()       { docxMergedCellsDetected++; }
    public void addDocxHorizontalSpans(int n)      { docxHorizontalSpansDetected += n; }
    public void incDocxVerticalMerges()            { docxVerticalMergesDetected++; }
    public void incDocxTablesUsingMarkdownBridge() { docxTablesUsingMarkdownBridge++; }
    public void incDocxTablesUsingRawTableModel()  { docxTablesUsingRawTableModel++; }
    public void incGroupContextPopulated()         { groupContextPopulatedCount++; }

    public void addRawTableCoordinateStats(RawTableModel table) {
        if (table == null || table.rows() == null) {
            return;
        }
        pageAttributionPhysicalCount++;
        for (RawTableRow row : table.rows()) {
            for (RawTableCell cell : row.cells()) {
                if (cell.hasCoordinates()) {
                    rawTableCellsWithCoordinates++;
                } else {
                    rawTableCellsMissingCoordinates++;
                }
            }
        }
    }

    public void incStructuredTablesNormalized() {
        structuredTablesNormalized++;
    }

    public void incMarkdownTablesNormalizedLegacy() {
        markdownTablesNormalizedLegacy++;
    }

    public void tallyFromChunks(java.util.List<KLTN.RAG_CHATBOT_BE.record.DocumentChunk> chunks) {
        textChunks = 0;
        normalizedTableRowChunks = 0;
        tableSummaryChunks = 0;
        for (var c : chunks) {
            if (c == null || c.chunkType() == null) {
                continue;
            }
            switch (c.chunkType()) {
                case "text", "section_summary", "parent_section_summary" -> textChunks++;
                case "normalized_table_row" -> normalizedTableRowChunks++;
                case "table_summary" -> tableSummaryChunks++;
                default -> { }
            }
        }
    }
}
