package KLTN.RAG_CHATBOT_BE.domain.document;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

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
}
