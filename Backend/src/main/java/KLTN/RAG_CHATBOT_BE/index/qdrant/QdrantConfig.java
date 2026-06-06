package KLTN.RAG_CHATBOT_BE.index.qdrant;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Slf4j
@Configuration
public class QdrantConfig {

    @Value("${qdrant.host}")
    private String host;

    @Value("${qdrant.http-port}")
    private int httpPort;

    @Value("${qdrant.collection-name}")
    private String collectionName;

    @Value("${qdrant.vector-size}")
    private int vectorSize;

    @Bean
    public ApplicationRunner initQdrantCollection() {
        return args -> {
            String baseUrl = "http://" + host + ":" + httpPort;
            RestClient restClient = RestClient.create();

            try {
                restClient.get()
                        .uri(baseUrl + "/collections/" + collectionName)
                        .retrieve()
                        .toBodilessEntity();
                log.info("Qdrant collection '{}' đã tồn tại", collectionName);
            } catch (HttpClientErrorException.NotFound e) {
                restClient.put()
                        .uri(baseUrl + "/collections/" + collectionName)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("vectors", Map.of("size", vectorSize, "distance", "Cosine")))
                        .retrieve()
                        .toBodilessEntity();
                log.info("Đã tạo Qdrant collection '{}' với {} dimensions", collectionName, vectorSize);
            }
        };
    }
}
