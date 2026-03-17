package KLTN.RAG_CHATBOT_BE.domain.document;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import java.time.LocalDateTime;

@Entity
@Table(name = "documents")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String fileName; // Tên file gốc: "quy_che_tuyen_sinh.pdf"

    @Column(nullable = false)
    private String filePath; // Đường dẫn lưu trên server

    @Column(nullable = false)
    private String fileType; // "PDF" hoặc "TXT"

    @Column
    private Long fileSize; // Kích thước file (bytes)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocumentStatus status; // Trạng thái xử lý

    @Column
    private Integer chunkCount; // Số chunk đã tạo

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime processedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        status = DocumentStatus.PENDING;
    }

    // Enum trạng thái xử lý
    public enum DocumentStatus {
        PENDING, // Vừa upload, chưa xử lý
        PROCESSING, // Đang chunk + embed
        COMPLETED, // Xong, vector đã lưu vào Qdrant
        FAILED // Có lỗi xảy ra
    }
}