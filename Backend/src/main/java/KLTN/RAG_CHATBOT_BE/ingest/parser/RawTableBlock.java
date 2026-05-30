package KLTN.RAG_CHATBOT_BE.ingest.parser;

public record RawTableBlock(
        String markerId,
        RawTableModel table
) {}
