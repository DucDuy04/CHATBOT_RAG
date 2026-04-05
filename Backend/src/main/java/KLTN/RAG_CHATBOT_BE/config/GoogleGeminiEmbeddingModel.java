package KLTN.RAG_CHATBOT_BE.config;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class GoogleGeminiEmbeddingModel implements EmbeddingModel {

    private final String apiKey;
    private final String modelName;
    private final RestTemplate restTemplate = new RestTemplate();

    public GoogleGeminiEmbeddingModel(String apiKey, String modelName) {
        this.apiKey = apiKey;
        this.modelName = modelName;
    }

    @Override
    public Response<Embedding> embed(TextSegment textSegment) {
        String url = "https://generativelanguage.googleapis.com/v1beta/models/"
                + modelName + ":embedContent?key=" + apiKey;

        Map<String, Object> body = Map.of(
                "model", "models/" + modelName,
                "content", Map.of(
                        "parts", List.of(Map.of("text", textSegment.text()))));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Map> response = restTemplate.exchange(
                url, HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);

        Map<String, Object> embedding = (Map<String, Object>) response.getBody().get("embedding");
        List<Double> values = (List<Double>) embedding.get("values");

        float[] vector = new float[values.size()];
        for (int i = 0; i < values.size(); i++) {
            vector[i] = values.get(i).floatValue();
        }

        return Response.from(Embedding.from(vector));
    }

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
        List<Embedding> embeddings = new ArrayList<>();
        for (TextSegment segment : textSegments) {
            embeddings.add(embed(segment).content());
        }
        return Response.from(embeddings);
    }
}