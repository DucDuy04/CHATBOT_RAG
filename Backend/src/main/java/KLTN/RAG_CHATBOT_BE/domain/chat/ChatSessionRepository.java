package KLTN.RAG_CHATBOT_BE.domain.chat;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChatSessionRepository extends JpaRepository<ChatSession, UUID> {
    Optional<ChatSession> findBySessionKey(UUID sessionKey);

    Optional<ChatSession> findBySessionKeyAndWidgetConfigId(UUID sessionKey, UUID widgetConfigId);

    List<ChatSession> findByWidgetConfigIdOrderByUpdatedAtDesc(UUID widgetConfigId);

    Page<ChatSession> findByCreatedAtBetweenOrderByCreatedAtDesc(LocalDateTime from, LocalDateTime to, Pageable pageable);

    Page<ChatSession> findByWidgetConfigIdAndCreatedAtBetweenOrderByCreatedAtDesc(
            UUID widgetConfigId,
            LocalDateTime from,
            LocalDateTime to,
            Pageable pageable
    );

    @Query(
            value = """
                    SELECT s
                    FROM ChatSession s
                    JOIN FETCH s.widgetConfig w
                    WHERE s.deletedAt IS NULL
                      AND w.deletedAt IS NULL
                      AND s.createdAt >= :from
                      AND s.createdAt < :to
                      AND (:widgetId IS NULL OR w.id = :widgetId)
                    ORDER BY s.createdAt DESC
                    """,
            countQuery = """
                    SELECT COUNT(s)
                    FROM ChatSession s
                    JOIN s.widgetConfig w
                    WHERE s.deletedAt IS NULL
                      AND w.deletedAt IS NULL
                      AND s.createdAt >= :from
                      AND s.createdAt < :to
                      AND (:widgetId IS NULL OR w.id = :widgetId)
                    """
    )
    Page<ChatSession> findForAnalyticsRange(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("widgetId") UUID widgetId,
            Pageable pageable
    );

    @Query("""
            SELECT s
            FROM ChatSession s
            JOIN FETCH s.widgetConfig w
            WHERE s.deletedAt IS NULL
              AND w.deletedAt IS NULL
              AND s.createdAt >= :from
              AND s.createdAt < :to
              AND (:widgetId IS NULL OR w.id = :widgetId)
            ORDER BY s.createdAt DESC
            """)
    List<ChatSession> findAllForAnalyticsRange(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("widgetId") UUID widgetId
    );
}

