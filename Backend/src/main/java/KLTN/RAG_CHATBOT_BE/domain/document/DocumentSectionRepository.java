package KLTN.RAG_CHATBOT_BE.domain.document;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
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

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE DocumentSection s SET s.deletedAt = :ts WHERE s.document.id = :documentId AND s.deletedAt IS NULL")
    int softDeleteByDocumentId(@Param("documentId") UUID documentId, @Param("ts") LocalDateTime ts);

    /** Hard-delete all section rows for a document (FAILED retry cleanup). Tables/chunks must be removed first. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM DocumentSection s WHERE s.document.id = :documentId")
    int hardDeleteByDocumentId(@Param("documentId") UUID documentId);
}
