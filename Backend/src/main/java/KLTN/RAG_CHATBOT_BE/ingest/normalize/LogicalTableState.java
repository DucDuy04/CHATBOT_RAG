package KLTN.RAG_CHATBOT_BE.ingest.normalize;

import java.util.List;

/**
 * Carries header/schema across continuation pages (task 23J).
 */
public record LogicalTableState(
        String logicalTableId,
        String tableName,
        List<String> originalHeaders,
        List<String> normalizedHeaderKeys,
        int columnCount,
        int lastPage,
        String sectionTitle,
        String groupContext,
        int rowIndexCounter
) {
    public LogicalTableState withRowCounter(int next) {
        return new LogicalTableState(
                logicalTableId, tableName, originalHeaders, normalizedHeaderKeys,
                columnCount, lastPage, sectionTitle, groupContext, next);
    }

    public LogicalTableState withGroupContext(String ctx) {
        return new LogicalTableState(
                logicalTableId, tableName, originalHeaders, normalizedHeaderKeys,
                columnCount, lastPage, sectionTitle, ctx, rowIndexCounter);
    }

    public LogicalTableState withLastPage(int page) {
        return new LogicalTableState(
                logicalTableId, tableName, originalHeaders, normalizedHeaderKeys,
                columnCount, page, sectionTitle, groupContext, rowIndexCounter);
    }
}
