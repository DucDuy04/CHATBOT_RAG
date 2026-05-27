package KLTN.RAG_CHATBOT_BE.service;

import java.util.List;

public record RawTableModel(
        String tableId,
        String documentId,
        int pageNumber,
        int tableIndexOnPage,
        ExtractorType extractorType,
        double x,
        double y,
        double width,
        double height,
        List<RawTableRow> rows,
        String titleCandidate,
        String rawPageContext,
        String sectionContext
) {
    public enum ExtractorType {
        SPREADSHEET,
        BASIC
    }
}
