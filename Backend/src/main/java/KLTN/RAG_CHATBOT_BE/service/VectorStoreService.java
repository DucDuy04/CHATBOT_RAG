// package KLTN.RAG_CHATBOT_BE.service;

// import java.util.List;
// import java.util.stream.Collectors;

// import org.springframework.ai.document.Document;
// import org.springframework.ai.vectorstore.SearchRequest;
// import org.springframework.ai.vectorstore.VectorStore;
// import org.springframework.stereotype.Service;

// import lombok.RequiredArgsConstructor;

// @Service
// @RequiredArgsConstructor
// public class VectorSearchService {

// private final VectorStore vectorStore;

// public void saveDocument(String text) {
// Document document = new Document(text);
// vectorStore.add(List.of(document));
// }

// public String searchRelevantContext(String query) {
// SearchRequest searchRequest = SearchRequest.builder()
// .query(query)
// .topK(3)
// .build();

// List<Document> results = vectorStore.similaritySearch(searchRequest);

// if (results == null || results.isEmpty()) {
// return "";
// }

// return results.stream()
// .map(doc -> doc.getText())
// .collect(Collectors.joining("\n"));
// }
// }
