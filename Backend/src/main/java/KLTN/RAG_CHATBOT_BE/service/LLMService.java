package KLTN.RAG_CHATBOT_BE.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class LLMService {

    private final ChatClient chatClient;

    public LLMService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    public String generateAnswer(String question, String context) {
        String promptTemplate = """
                Ban la mot tro ly ao than thien tren website.
                Hay su dung cac thong tin trong phan CONTEXT duoi day de tra loi cau hoi cua nguoi dung.
                Neu thong tin khong co trong CONTEXT, hay tra loi la \"Toi khong biet\", khong duoc tu bia ra thong tin.

                CONTEXT:
                {context}

                CAU HOI CUA NGUOI DUNG:
                {question}
                """;

        String finalPrompt = promptTemplate
                .replace("{context}", context == null ? "" : context)
                .replace("{question}", question == null ? "" : question);

        return chatClient.prompt()
                .user(finalPrompt)
                .call()
                .content();
    }
}
