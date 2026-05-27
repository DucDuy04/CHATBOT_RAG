package KLTN.RAG_CHATBOT_BE.service;

import java.util.List;

public record RawTableRow(
        int physicalRowIndex,
        List<RawTableCell> cells
) {}
