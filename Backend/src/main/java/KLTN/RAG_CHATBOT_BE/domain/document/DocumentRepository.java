package KLTN.RAG_CHATBOT_BE.domain.document;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

import KLTN.RAG_CHATBOT_BE.domain.enums.DocumentStatus;

@Repository
public interface DocumentRepository extends JpaRepository<Document, UUID> {

    // Lấy tất cả document đã xử lý xong
    List<Document> findByStatusOrderByCreatedAtDesc(DocumentStatus status);

    List<Document> findByWidgetConfigId(UUID widgetConfigId);

    // tìm kiếm tất cả document của widget có tên file chứa keyword (dùng cho chức năng search document)
    // List<Document> findByWidgetConfigIdAndFileNameContainingIgnoreCaseOrderByCreatedAtDesc(UUID widgetConfigId, String keyword);
}