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
