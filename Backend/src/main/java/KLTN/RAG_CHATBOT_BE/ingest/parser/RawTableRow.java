package KLTN.RAG_CHATBOT_BE.ingest.parser;

import java.util.List;

public record RawTableRow(
        int physicalRowIndex,
        List<RawTableCell> cells
) {}
