package KLTN.RAG_CHATBOT_BE.domain.chat;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChatFeedbackRepository extends JpaRepository<ChatFeedback, UUID> {

    Optional<ChatFeedback> findByMessageId(UUID messageId);

    @Query("""
            SELECT COUNT(f)
            FROM ChatFeedback f
            JOIN f.message m
            JOIN m.session s
            JOIN s.widgetConfig w
            WHERE f.deletedAt IS NULL
              AND m.deletedAt IS NULL
              AND s.deletedAt IS NULL
              AND w.deletedAt IS NULL
              AND f.createdAt >= :from
              AND f.createdAt < :to
              AND (:widgetId IS NULL OR w.id = :widgetId)
            """)
    long countInRange(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("widgetId") UUID widgetId
    );

    @Query("""
            SELECT COUNT(f)
            FROM ChatFeedback f
            JOIN f.message m
            JOIN m.session s
            JOIN s.widgetConfig w
            WHERE f.deletedAt IS NULL
              AND m.deletedAt IS NULL
              AND s.deletedAt IS NULL
              AND w.deletedAt IS NULL
              AND f.createdAt >= :from
              AND f.createdAt < :to
              AND f.rating = :rating
              AND (:widgetId IS NULL OR w.id = :widgetId)
            """)
    long countByRatingInRange(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("widgetId") UUID widgetId,
            @Param("rating") int rating
    );

    @Query("""
            SELECT m.session.id,
                   SUM(CASE WHEN f.rating = 1 THEN 1 ELSE 0 END),
                   SUM(CASE WHEN f.rating = -1 THEN 1 ELSE 0 END)
            FROM ChatFeedback f
            JOIN f.message m
            WHERE f.deletedAt IS NULL
              AND m.deletedAt IS NULL
              AND m.session.id IN :sessionIds
            GROUP BY m.session.id
            """)
    List<Object[]> aggregateBySessionIds(@Param("sessionIds") List<UUID> sessionIds);
}
