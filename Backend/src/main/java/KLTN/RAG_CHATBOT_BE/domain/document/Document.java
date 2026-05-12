package KLTN.RAG_CHATBOT_BE.domain.document;

// import jakarta.persistence.*;
// import lombok.Getter;
// import lombok.Setter;
// import lombok.NoArgsConstructor;
// import lombok.AllArgsConstructor;
// import lombok.Builder;
// import java.time.LocalDateTime;

// @Entity
// @Table(name = "documents")
// @Getter
// @Setter
// @NoArgsConstructor
// @AllArgsConstructor
// @Builder
// public class Document {

//     @Id
//     @GeneratedValue(strategy = GenerationType.IDENTITY)
//     private Long id;

//     @Column(nullable = false)
//     private String fileName; // Tên file gốc: "quy_che_tuyen_sinh.pdf"

//     @Column(nullable = false)
//     private String filePath; // Đường dẫn lưu trên server

//     @Column(nullable = false)
//     private String fileType; // "PDF" hoặc "TXT"

//     @Column
//     private Long fileSize; // Kích thước file (bytes)

//     @Enumerated(EnumType.STRING)
//     @Column(nullable = false)
//     private DocumentStatus status; // Trạng thái xử lý

//     @Column
//     private Integer chunkCount; // Số chunk đã tạo

//     @Column(nullable = false)
//     private LocalDateTime createdAt;

//     @Column
//     private LocalDateTime processedAt;

//     @PrePersist
//     protected void onCreate() {
//         createdAt = LocalDateTime.now();
//         status = DocumentStatus.PENDING;
//     }

//     // Enum trạng thái xử lý
//     public enum DocumentStatus {
//         PENDING, // Vừa upload, chưa xử lý
//         PROCESSING, // Đang chunk + embed
//         COMPLETED, // Xong, vector đã lưu vào Qdrant
//         FAILED // Có lỗi xảy ra
//     }
// }

import KLTN.RAG_CHATBOT_BE.domain.enums.DocumentStatus;
import jakarta.persistence.*;
import lombok.*;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

import KLTN.RAG_CHATBOT_BE.domain.widget.WidgetConfig;

@Entity
@Table(name = "documents")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
@SQLRestriction("deleted_at IS NULL")
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "widget_config_id", nullable = false)
    private WidgetConfig widgetConfig;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "file_path", nullable = false)
    private String filePath;

    @Column(name = "file_type", nullable = false)
    private String fileType;

    @Column(name = "mime_type")
    private String mimeType;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(unique = true)
    private String checksum;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private DocumentStatus status = DocumentStatus.PENDING;

    @Column(name = "chunk_count")
    private Integer chunkCount;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}