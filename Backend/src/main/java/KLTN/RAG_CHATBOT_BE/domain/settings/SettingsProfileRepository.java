package KLTN.RAG_CHATBOT_BE.domain.settings;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SettingsProfileRepository extends JpaRepository<SettingsProfile, UUID> {
}
