package KLTN.RAG_CHATBOT_BE.domain.chat;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

    @Query("""
            SELECT COUNT(m) FROM ChatMessage m
            JOIN m.session s
            WHERE s.widgetConfig.id = :widgetId
            AND m.deletedAt IS NULL
            AND s.deletedAt IS NULL
            """)
    long countByWidgetConfigId(@Param("widgetId") UUID widgetId);

    // Lấy 10 tin nhắn gần nhất của 1 session để đưa vào context
    List<ChatMessage> findTop10BySessionIdOrderByCreatedAtAsc(UUID sessionId);

    List<ChatMessage> findBySessionIdOrderByCreatedAtAsc(UUID sessionId);

    long countBySessionId(UUID sessionId);

    ChatMessage findTopBySessionIdOrderByCreatedAtDesc(UUID sessionId);
}