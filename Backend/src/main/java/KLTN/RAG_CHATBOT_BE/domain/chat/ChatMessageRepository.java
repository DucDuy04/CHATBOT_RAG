package KLTN.RAG_CHATBOT_BE.domain.chat;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
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

    long countByCreatedAtBetween(LocalDateTime from, LocalDateTime to);

    List<ChatMessage> findByCreatedAtBetweenOrderByCreatedAtAsc(LocalDateTime from, LocalDateTime to);

    List<ChatMessage> findTop50ByOrderByCreatedAtDesc();

    @Query("""
            SELECT m
            FROM ChatMessage m
            JOIN FETCH m.session s
            JOIN FETCH s.widgetConfig w
            WHERE m.deletedAt IS NULL
              AND s.deletedAt IS NULL
              AND w.deletedAt IS NULL
            ORDER BY m.createdAt DESC
            """)
    List<ChatMessage> findLatestForDashboard(Pageable pageable);

    @Query("""
            SELECT s.widgetConfig.id, s.widgetConfig.name, COUNT(m)
            FROM ChatMessage m
            JOIN m.session s
            WHERE m.deletedAt IS NULL
              AND s.deletedAt IS NULL
              AND s.widgetConfig.deletedAt IS NULL
            GROUP BY s.widgetConfig.id, s.widgetConfig.name
            ORDER BY COUNT(m) DESC
            """)
    List<Object[]> findTopChatbotMessageCounts(Pageable pageable);

    @Query("""
            SELECT m
            FROM ChatMessage m
            JOIN FETCH m.session s
            JOIN FETCH s.widgetConfig w
            WHERE m.deletedAt IS NULL
              AND s.deletedAt IS NULL
              AND w.deletedAt IS NULL
              AND m.createdAt >= :from
              AND m.createdAt < :to
              AND (:widgetId IS NULL OR w.id = :widgetId)
            ORDER BY m.createdAt ASC
            """)
    List<ChatMessage> findForAnalyticsRange(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("widgetId") UUID widgetId
    );
}