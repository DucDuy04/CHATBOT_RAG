package KLTN.RAG_CHATBOT_BE.domain.document;

import KLTN.RAG_CHATBOT_BE.domain.widget.WidgetConfig;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "document_chunks", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"document_id", "chunk_index"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@SQLRestriction("deleted_at IS NULL")
public class DocumentChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "widget_config_id")
    private WidgetConfig widgetConfig;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "section_db_id")
    private DocumentSection section;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "table_db_id")
    private DocumentTable table;

    @Column(name = "chunk_index", nullable = false)
    private Integer chunkIndex;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(name = "qdrant_point_id", unique = true)
    private UUID qdrantPointId;

    @Column(name = "section_id", length = 64)
    private String sectionId;

    @Column(name = "parent_id", length = 64)
    private String parentId;

    @Column(name = "table_id", length = 64)
    private String tableId;

    @Column(name = "chunk_type", nullable = false, length = 50)
    @Builder.Default
    private String chunkType = "text";

    @Column(name = "section_title", length = 500)
    private String sectionTitle;

    @Column(name = "heading_path_text", length = 1000)
    private String headingPathText;

    @Column(name = "page_start")
    private Integer pageStart;

    @Column(name = "page_end")
    private Integer pageEnd;

    @Column(name = "order_index")
    private Integer orderIndex;

    /**
     * Thứ tự section trong tài liệu (0-based).
     * Cho phép sort lại đúng thứ tự tài liệu gốc khi reconstruct context từ Qdrant.
     */
    @Column(name = "section_order")
    private Integer sectionOrder;

    /**
     * Cấp độ heading (1=top-level, 2=subsection, 3=sub-subsection, 0=không có số heading).
     */
    @Column(name = "heading_level")
    private Integer headingLevel;

    /**
     * Comma-separated sectionIds của các subsection trực tiếp.
     * Chỉ có giá trị khi chunkType="parent_section_summary".
     * Dùng trong retrieval để tự động expand sang toàn bộ child sections.
     * Ví dụ: "sec_6.1,sec_6.2,sec_6.3"
     */
    @Column(name = "child_section_ids", length = 2000)
    private String childSectionIds;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prev_chunk_id")
    private DocumentChunk prevChunk;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "next_chunk_id")
    private DocumentChunk nextChunk;

    @Column(name = "token_count")
    private Integer tokenCount;

    @Column(name = "source_file", length = 255)
    private String sourceFile;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "table_name", length = 1000)
    private String tableName;

    @Column(name = "row_index")
    private Integer rowIndex;

    @Column(name = "cells_json", columnDefinition = "TEXT")
    private String cellsJson;

    @Column(name = "group_context", columnDefinition = "TEXT")
    private String groupContext;
}
