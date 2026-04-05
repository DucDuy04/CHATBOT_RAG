package KLTN.RAG_CHATBOT_BE.config;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.nomic.NomicEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GroqConfig {

    // Groq config
    @Value("${groq.api-key}")
    private String groqApiKey;

    @Value("${groq.chat-model}")
    private String groqChatModel;

    @Value("${groq.base-url}")
    private String groqBaseUrl;

    // Nomic config
    @Value("${nomic.api-key}")
    private String nomicApiKey;

    @Value("${nomic.embedding-model}")
    private String nomicEmbeddingModel;

    // Groq dùng OpenAI-compatible API
    // nên LangChain4j dùng OpenAiChatModel với baseUrl trỏ sang Groq
    @Bean
    public OpenAiChatModel chatModel() {
        return OpenAiChatModel.builder()
                .apiKey(groqApiKey)
                .baseUrl(groqBaseUrl)
                .modelName(groqChatModel)
                .temperature(0.7)
                .maxTokens(1000)
                .build();
    }

    // Embedding dùng Nomic thay vì Groq
    @Bean
    public EmbeddingModel embeddingModel() {
        return NomicEmbeddingModel.builder()
                .apiKey(nomicApiKey)
                .modelName(nomicEmbeddingModel)
                .build();
    }
}