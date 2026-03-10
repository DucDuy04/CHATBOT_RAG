package KLTN.RAG_CHATBOT_BE.controller;

import KLTN.RAG_CHATBOT_BE.model.IngestRequest;
import KLTN.RAG_CHATBOT_BE.service.RAGService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class IngestController {

    private final RAGService ragService;

    @PostMapping("/ingest")
    public ResponseEntity<String> ingest(@RequestBody IngestRequest request) {
        String text = request == null ? "" : request.text();
        ragService.ingestData(text);
        return ResponseEntity.ok("Du lieu da duoc nap thanh cong vao Qdrant!");
    }
}
