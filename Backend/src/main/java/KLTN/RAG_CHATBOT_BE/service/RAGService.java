package KLTN.RAG_CHATBOT_BE.service;

import org.springframework.stereotype.Service;


import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RAGService {

    private final VectorSearchService vectorSearchService;
    private final LLMService llmService;

    public String chat(String userMessage) {
        String context = vectorSearchService.searchRelevantContext(userMessage);
        return llmService.generateAnswer(userMessage, context);
    }

    public void ingestData(String text) {
        vectorSearchService.saveDocument(text);
    }
}
