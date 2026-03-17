package KLTN.RAG_CHATBOT_BE.domain.document;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {

    // Lấy tất cả document đã xử lý xong
    List<Document> findByStatusOrderByCreatedAtDesc(Document.DocumentStatus status);
}