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

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE DocumentTable t SET t.deletedAt = :ts WHERE t.document.id = :documentId AND t.deletedAt IS NULL")
    int softDeleteByDocumentId(@Param("documentId") UUID documentId, @Param("ts") LocalDateTime ts);

    /** Hard-delete all table rows for a document (FAILED retry cleanup). Chunks must be removed first. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM DocumentTable t WHERE t.document.id = :documentId")
    int hardDeleteByDocumentId(@Param("documentId") UUID documentId);
}
