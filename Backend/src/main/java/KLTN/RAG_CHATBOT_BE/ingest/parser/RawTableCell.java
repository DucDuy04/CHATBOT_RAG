package KLTN.RAG_CHATBOT_BE.ingest.parser;

public record RawTableCell(
        String text,
        int physicalRowIndex,
        int physicalColIndex,
        int pageNumber,
        double x,
        double y,
        double width,
        double height,
        double xEnd,
        double yEnd,
        int tableIndexOnPage,
        RawTableModel.ExtractorType extractorType,
        String provenanceId
) {
    public boolean hasCoordinates() {
        return width > 0.0 && height > 0.0;
    }

    public double centerX() {
        return x + width / 2.0;
    }
}
