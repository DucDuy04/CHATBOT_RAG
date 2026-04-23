package KLTN.RAG_CHATBOT_BE.record;

public record Section(
    String header, 
    int startPage, 
    int endPage, 
    String content
) {}