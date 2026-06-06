package KLTN.RAG_CHATBOT_BE.domain.document;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, UUID> {

    List<DocumentChunk> findByDocumentId(UUID documentId);

    List<DocumentChunk> findByDocumentIdOrderByChunkIndexAsc(UUID documentId);

    List<DocumentChunk> findByDocumentIdOrderByOrderIndexAsc(UUID documentId);

    List<DocumentChunk> findByWidgetConfigIdOrderByDocumentIdAscOrderIndexAsc(UUID widgetConfigId);

    List<DocumentChunk> findByWidgetConfigIdAndSectionIdInOrderByDocumentIdAscOrderIndexAsc(
            UUID widgetConfigId,
            Collection<String> sectionIds
    );

    List<DocumentChunk> findByWidgetConfigIdAndTableIdInOrderByDocumentIdAscOrderIndexAsc(
            UUID widgetConfigId,
            Collection<String> tableIds
    );

    List<DocumentChunk> findByWidgetConfigIdAndIdIn(
            UUID widgetConfigId,
            Collection<UUID> ids
    );

    List<DocumentChunk> findByWidgetConfigIdAndDocumentIdAndOrderIndexBetweenOrderByOrderIndexAsc(
            UUID widgetConfigId,
            UUID documentId,
            Integer from,
            Integer to
    );

    /**
     * Lấy tất cả chunk theo parentId (dùng cho sibling expansion:
     * khi biết "parent_6", lấy toàn bộ 6.1, 6.2, 6.3, 6.4, 6.5).
     */
    List<DocumentChunk> findByWidgetConfigIdAndParentIdInOrderByDocumentIdAscOrderIndexAsc(
            UUID widgetConfigId,
            Collection<String> parentIds
    );

    /**
     * Lấy chunk chỉ trong tập document IDs cho trước — dùng để thay thế full-table-scan
     * trong lexical anchoring và section range expansion.
     * Giảm từ O(total_chunks_per_widget) xuống O(chunks_in_relevant_docs).
     */
    List<DocumentChunk> findByWidgetConfigIdAndDocumentIdInOrderByDocumentIdAscOrderIndexAsc(
            UUID widgetConfigId,
            Collection<UUID> documentIds
    );

    /**
     * Lightweight active-chunk check for startup prewarm — respects
     * {@code @SQLRestriction("deleted_at IS NULL")} on {@link DocumentChunk}.
     * Returns {@code true} only when at least one non-deleted chunk exists for the widget.
     */
    boolean existsByWidgetConfigId(UUID widgetConfigId);

    /**
     * Soft-delete all chunks for a document (same {@code deleted_at} as parent document).
     * Keeps rows for audit; {@code @SQLRestriction} on {@link DocumentChunk} then hides them from retrieval.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE DocumentChunk c SET c.deletedAt = :ts WHERE c.document.id = :documentId AND c.deletedAt IS NULL")
    int softDeleteByDocumentId(@Param("documentId") UUID documentId, @Param("ts") LocalDateTime ts);

    /**
     * Break self-referential links before hard-delete (DB FK on {@code prev_chunk_id}/{@code next_chunk_id}).
     * Applies to all rows for the document, including any soft-deleted partial-ingestion rows.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE DocumentChunk c SET c.prevChunk = NULL, c.nextChunk = NULL WHERE c.document.id = :documentId")
    int unlinkNeighborsByDocumentId(@Param("documentId") UUID documentId);

    /**
     * Hard-delete every chunk row for a document (retry after FAILED partial ingestion).
     * Must run after {@link #unlinkNeighborsByDocumentId}; tables/sections deleted separately.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM DocumentChunk c WHERE c.document.id = :documentId")
    int hardDeleteByDocumentId(@Param("documentId") UUID documentId);
}
