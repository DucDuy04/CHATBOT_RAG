// package KLTN.RAG_CHATBOT_BE.service;

// import KLTN.RAG_CHATBOT_BE.domain.chat.ChatMessage;
// import KLTN.RAG_CHATBOT_BE.domain.chat.ChatMessageRepository;
// import KLTN.RAG_CHATBOT_BE.dto.ChatRequest;
// import KLTN.RAG_CHATBOT_BE.dto.ChatResponse;
// import dev.langchain4j.data.segment.TextSegment;
// import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
// import dev.langchain4j.store.embedding.EmbeddingMatch;
// import lombok.RequiredArgsConstructor;
// import lombok.extern.slf4j.Slf4j;
// import org.springframework.stereotype.Service;
// import java.util.List;

// @Slf4j
// @Service
// @RequiredArgsConstructor
// public class ChatService {

// private final EmbeddingService embeddingService;
// private final PromptBuilderService promptBuilderService;
// private final GoogleAiGeminiChatModel chatModel;
// private final ChatMessageRepository chatMessageRepository;

// private static final int TOP_K = 5; // Lấy 5 chunk liên quan nhất
// private static final double MIN_SIMILARITY = 0.7; // Ngưỡng điểm tương đồng
// tối thiểu

// public ChatResponse chat(ChatRequest request) {
// String sessionId = request.getSessionId();
// String question = request.getMessage();

// log.info("Nhận câu hỏi từ session={}: {}", sessionId, question);

// // Bước 1: Lưu câu hỏi người dùng vào DB
// saveChatMessage(sessionId, ChatMessage.MessageRole.USER, question, null);

// // Bước 2: Tìm kiếm vector trong Qdrant
// List<EmbeddingMatch<TextSegment>> matches = embeddingService.search(question,
// TOP_K);

// // Bước 3: Lọc theo ngưỡng similarity
// List<EmbeddingMatch<TextSegment>> relevantMatches = matches.stream()
// .filter(match -> match.score() >= MIN_SIMILARITY)
// .toList();

// log.info("Tìm được {} chunks liên quan (score >= {})",
// relevantMatches.size(), MIN_SIMILARITY);

// // Bước 4: Tách text và metadata từ kết quả tìm kiếm
// List<String> contextChunks = relevantMatches.stream()
// .map(match -> match.embedded().text())
// .toList();

// List<ChatResponse.SourceDto> sources = relevantMatches.stream()
// .map(match -> ChatResponse.SourceDto.builder()
// .fileName(match.embedded().metadata().getString("fileName"))
// .chunkText(match.embedded().text())
// .build())
// .toList();

// // Bước 5: Lấy lịch sử chat gần nhất
// List<ChatMessage> chatHistory =
// chatMessageRepository.findTop10BySessionIdOrderByCreatedAtAsc(sessionId);

// // Bước 6: Xây dựng prompt
// String prompt = promptBuilderService.buildPrompt(
// question, contextChunks, chatHistory);

// // Bước 7: Gọi GPT
// String answer;
// if (relevantMatches.isEmpty()) {
// // Không tìm được context liên quan → trả lời mặc định
// answer = "Tôi không tìm thấy thông tin liên quan đến câu hỏi của bạn trong
// tài liệu đã cung cấp.";
// } else {
// answer = chatModel.generate(prompt);
// }

// log.info("GPT trả lời xong cho session={}", sessionId);

// // Bước 8: Lưu câu trả lời vào DB
// saveChatMessage(sessionId, ChatMessage.MessageRole.ASSISTANT, answer, null);

// return ChatResponse.builder()
// .answer(answer)
// .sources(sources)
// .build();
// }

// private void saveChatMessage(String sessionId,
// ChatMessage.MessageRole role,
// String content,
// String sources) {
// ChatMessage message = ChatMessage.builder()
// .sessionId(sessionId)
// .role(role)
// .content(content)
// .sources(sources)
// .build();
// chatMessageRepository.save(message);
// }
// }
package KLTN.RAG_CHATBOT_BE.service;

import KLTN.RAG_CHATBOT_BE.domain.chat.ChatMessage;
import KLTN.RAG_CHATBOT_BE.domain.chat.ChatMessageRepository;
import KLTN.RAG_CHATBOT_BE.dto.ChatRequest;
import KLTN.RAG_CHATBOT_BE.dto.ChatResponse;
import dev.langchain4j.data.segment.TextSegment;
// import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

        private final EmbeddingService embeddingService;
        private final PromptBuilderService promptBuilderService;
        private final OpenAiChatModel chatModel;
        private final ChatMessageRepository chatMessageRepository;

        private static final int TOP_K = 5;

        public ChatResponse chat(ChatRequest request) {
                String sessionId = request.getSessionId();
                String question = request.getMessage();

                log.info("Nhận câu hỏi từ session={}: {}", sessionId, question);

                // Bước 1: Lưu câu hỏi người dùng vào DB
                saveChatMessage(sessionId, ChatMessage.MessageRole.USER, question, null);

                // Bước 2: Tìm kiếm vector trong Qdrant
                List<TextSegment> segments = embeddingService.search(question, TOP_K);
                log.info("Tìm được {} chunks liên quan", segments.size());

                // Bước 3: Tách text và metadata
                List<String> contextChunks = segments.stream()
                                .map(TextSegment::text)
                                .toList();

                List<ChatResponse.SourceDto> sources = segments.stream()
                                .map(seg -> ChatResponse.SourceDto.builder()
                                                .fileName(seg.metadata().getString("fileName"))
                                                .chunkText(seg.text())
                                                .build())
                                .toList();

                // Bước 4: Lấy lịch sử chat
                List<ChatMessage> chatHistory = chatMessageRepository
                                .findTop10BySessionIdOrderByCreatedAtAsc(sessionId);

                // Bước 5: Xây dựng prompt
                String prompt = promptBuilderService.buildPrompt(question, contextChunks, chatHistory);

                // Bước 6: Gọi Gemini
                String answer;
                if (segments.isEmpty()) {
                        answer = "Tôi không tìm thấy thông tin liên quan đến câu hỏi của bạn trong tài liệu đã cung cấp.";
                } else {
                        answer = chatModel.generate(prompt);
                }

                log.info("Gemini trả lời xong cho session={}", sessionId);

                // Bước 7: Lưu câu trả lời vào DB
                saveChatMessage(sessionId, ChatMessage.MessageRole.ASSISTANT, answer, null);

                return ChatResponse.builder()
                                .answer(answer)
                                .sources(sources)
                                .build();
        }

        private void saveChatMessage(String sessionId,
                        ChatMessage.MessageRole role,
                        String content,
                        String sources) {
                ChatMessage message = ChatMessage.builder()
                                .sessionId(sessionId)
                                .role(role)
                                .content(content)
                                .sources(sources)
                                .build();
                chatMessageRepository.save(message);
        }
}