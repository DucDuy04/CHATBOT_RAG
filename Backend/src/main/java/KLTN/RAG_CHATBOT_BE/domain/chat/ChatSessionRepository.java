package KLTN.RAG_CHATBOT_BE.domain.chat;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChatSessionRepository extends JpaRepository<ChatSession, UUID> {
    Optional<ChatSession> findBySessionKey(UUID sessionKey);

    Optional<ChatSession> findBySessionKeyAndWidgetConfigId(UUID sessionKey, UUID widgetConfigId);
}

