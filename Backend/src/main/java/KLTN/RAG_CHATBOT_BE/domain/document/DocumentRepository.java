package KLTN.RAG_CHATBOT_BE.domain.document;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import KLTN.RAG_CHATBOT_BE.domain.enums.DocumentStatus;

@Repository
public interface DocumentRepository extends JpaRepository<Document, UUID>, JpaSpecificationExecutor<Document> {

    long countByWidgetConfig_Id(UUID widgetConfigId);

    // Lấy tất cả document đã xử lý xong
    List<Document> findByStatusOrderByCreatedAtDesc(DocumentStatus status);

    List<Document> findByWidgetConfigId(UUID widgetConfigId);
    List<Document> findTop50ByOrderByUpdatedAtDesc();

    // tìm kiếm tất cả document của widget có tên file chứa keyword (dùng cho chức năng search document)
    // List<Document> findByWidgetConfigIdAndFileNameContainingIgnoreCaseOrderByCreatedAtDesc(UUID widgetConfigId, String keyword);

    /**
     * Resolve widget UUID from FK column without loading {@code WidgetConfig} entity
     * (needed when widget row is soft-deleted but document row still references it).
     */
    @Query(value = "SELECT BIN_TO_UUID(widget_config_id) FROM documents WHERE id = :id AND deleted_at IS NULL LIMIT 1",
            nativeQuery = true)
    Optional<String> findWidgetConfigIdUuidStringForPurge(@Param("id") UUID id);
}