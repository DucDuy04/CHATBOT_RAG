package KLTN.RAG_CHATBOT_BE.domain.document;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocumentSectionRepository extends JpaRepository<DocumentSection, UUID> {

    List<DocumentSection> findByDocumentIdOrderByOrderIndexAsc(UUID documentId);

    List<DocumentSection> findByWidgetConfigIdAndSectionKeyIn(
            UUID widgetConfigId,
            Collection<String> sectionKeys
    );

    Optional<DocumentSection> findByDocumentIdAndSectionKey(
            UUID documentId,
            String sectionKey
    );

    List<DocumentSection> findTop200ByWidgetConfigIdOrderByOrderIndexAsc(UUID widgetConfigId);

    /** Full list without cap — needed for accurate descendant expansion in heading-lock mode. */
    List<DocumentSection> findByWidgetConfigIdOrderByOrderIndexAsc(UUID widgetConfigId);
}