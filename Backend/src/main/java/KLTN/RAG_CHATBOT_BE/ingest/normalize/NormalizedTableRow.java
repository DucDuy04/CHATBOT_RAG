package KLTN.RAG_CHATBOT_BE.ingest.normalize;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One normalized data row ready for chunk + Qdrant payload.
 */
public record NormalizedTableRow(
        String tableName,
        int rowIndex,
        Map<String, String> cells,
        int pageStart,
        int pageEnd,
        String groupContext,
        String canonicalText,
        String cellsJson
) {
    public static NormalizedTableRow of(
            String tableName,
            int rowIndex,
            Map<String, String> cells,
            int pageStart,
            int pageEnd,
            String groupContext
    ) {
        Map<String, String> ordered = new LinkedHashMap<>(cells);
        String canonical = NormalizedTableService.buildCanonicalText(
                tableName, rowIndex, ordered, groupContext, pageStart);
        String json = NormalizedTableService.cellsToJson(ordered);
        return new NormalizedTableRow(
                tableName, rowIndex, ordered, pageStart, pageEnd, groupContext, canonical, json);
    }
}
