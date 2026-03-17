package KLTN.RAG_CHATBOT_BE.domain.chat;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    // Lấy 10 tin nhắn gần nhất của 1 session để đưa vào context
    List<ChatMessage> findTop10BySessionIdOrderByCreatedAtAsc(String sessionId);
}