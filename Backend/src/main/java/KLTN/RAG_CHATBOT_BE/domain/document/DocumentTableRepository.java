package KLTN.RAG_CHATBOT_BE.domain.document;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocumentTableRepository extends JpaRepository<DocumentTable, UUID> {

    List<DocumentTable> findByDocumentIdOrderByOrderIndexAsc(UUID documentId);

    List<DocumentTable> findByWidgetConfigIdAndTableKeyIn(
            UUID widgetConfigId,
            Collection<String> tableKeys
    );

    List<DocumentTable> findByWidgetConfigIdAndSectionKeyIn(
            UUID widgetConfigId,
            Collection<String> sectionKeys
    );

    Optional<DocumentTable> findByDocumentIdAndTableKey(
            UUID documentId,
            String tableKey
    );
}