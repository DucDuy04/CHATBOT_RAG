package KLTN.RAG_CHATBOT_BE.config;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.googleai.GoogleAiEmbeddingModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GeminiConfig {

    @Value("${gemini.api-key}")
    private String apiKey;

    @Value("${gemini.embedding-model}")
    private String embeddingModel;

    @Value("${gemini.chat-model}")
    private String chatModel;

    // Bean tạo vector từ text
    @Bean
    public EmbeddingModel embeddingModel() {
        return GoogleAiEmbeddingModel.builder()
                .apiKey(apiKey)
                .modelName(embeddingModel)
                .build();
    }

    // Bean gọi Gemini để sinh câu trả lời
    @Bean
    public GoogleAiGeminiChatModel chatModel() {
        return GoogleAiGeminiChatModel.builder()
                .apiKey(apiKey)
                .modelName(chatModel)
                .temperature(0.7)
                .maxOutputTokens(1000)
                .build();
    }
}