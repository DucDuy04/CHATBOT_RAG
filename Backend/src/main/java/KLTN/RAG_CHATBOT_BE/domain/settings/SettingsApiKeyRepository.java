package KLTN.RAG_CHATBOT_BE.domain.settings;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SettingsApiKeyRepository extends JpaRepository<SettingsApiKey, UUID> {

    List<SettingsApiKey> findByDeletedAtIsNullOrderByCreatedAtDesc();

    Optional<SettingsApiKey> findByIdAndDeletedAtIsNull(UUID id);
}
