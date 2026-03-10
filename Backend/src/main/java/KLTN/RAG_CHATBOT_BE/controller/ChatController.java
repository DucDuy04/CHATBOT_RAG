package KLTN.RAG_CHATBOT_BE.controller;

import KLTN.RAG_CHATBOT_BE.model.ChatRequest;
import KLTN.RAG_CHATBOT_BE.model.ChatResponse;
import KLTN.RAG_CHATBOT_BE.service.RAGService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ChatController {

    private final RAGService ragService;

    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request) {
        String userMessage = request == null ? "" : request.message();
        String answer = ragService.chat(userMessage);
        return ResponseEntity.ok(new ChatResponse(answer));
    }
}
