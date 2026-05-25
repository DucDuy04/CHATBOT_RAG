package KLTN.RAG_CHATBOT_BE.service;

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
