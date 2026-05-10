package KLTN.RAG_CHATBOT_BE.service;

import KLTN.RAG_CHATBOT_BE.domain.chat.ChatFeedback;
import KLTN.RAG_CHATBOT_BE.domain.chat.ChatFeedbackRepository;
import KLTN.RAG_CHATBOT_BE.domain.chat.ChatMessage;
import KLTN.RAG_CHATBOT_BE.domain.chat.ChatMessageRepository;
import KLTN.RAG_CHATBOT_BE.domain.enums.MessageRole;
import KLTN.RAG_CHATBOT_BE.dto.ChatFeedbackResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChatFeedbackService {

    private final ChatMessageRepository chatMessageRepository;
    private final ChatFeedbackRepository chatFeedbackRepository;

    @Transactional
    public ChatFeedbackResponse submitFeedback(UUID messageId, int rating, String comment) {
        ChatMessage message = chatMessageRepository.findById(messageId)
                .orElseThrow(() -> new NoSuchElementException("Message not found"));

        if (message.getRole() != MessageRole.ASSISTANT) {
            throw new IllegalStateException("Feedback can only be submitted for assistant messages");
        }

        ChatFeedback feedback = chatFeedbackRepository.findByMessageId(messageId)
                .orElseGet(() -> ChatFeedback.builder()
                        .message(message)
                        .build());

        feedback.setRating(rating);
        feedback.setComment(comment == null ? "" : comment.trim());
        chatFeedbackRepository.save(feedback);

        return ChatFeedbackResponse.builder()
                .success(true)
                .messageId(messageId.toString())
                .rating(rating)
                .comment(feedback.getComment())
                .build();
    }
}
