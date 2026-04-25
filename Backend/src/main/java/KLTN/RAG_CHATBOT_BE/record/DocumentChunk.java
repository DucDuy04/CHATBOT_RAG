package KLTN.RAG_CHATBOT_BE.record;

public record DocumentChunk(
    String content,
    String header,
    int startPage,
    int endPage,
    String chunkType,
    String sectionId,
    String parentId,
    String tableId,
    String headingPathText,
    int orderIndex,
    int tokenCount
) {}