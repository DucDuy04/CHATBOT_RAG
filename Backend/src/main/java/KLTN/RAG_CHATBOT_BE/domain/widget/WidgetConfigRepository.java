package KLTN.RAG_CHATBOT_BE.domain.widget;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface WidgetConfigRepository extends JpaRepository<WidgetConfig, UUID> {
    Optional<WidgetConfig> findByApiKey(UUID apiKey);
}
