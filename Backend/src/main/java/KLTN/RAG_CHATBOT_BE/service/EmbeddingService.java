package KLTN.RAG_CHATBOT_BE.service;

import java.util.List;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EmbeddingService {

    private final EmbeddingModel embeddingModel;

   public float[] embedText(String text) {
    return embeddingModel.embed(text);
}
}
