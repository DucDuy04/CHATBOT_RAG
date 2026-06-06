package KLTN.RAG_CHATBOT_BE.domain.document;

import KLTN.RAG_CHATBOT_BE.domain.widget.WidgetConfig;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(
        name = "document_tables",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_document_tables_doc_table_key",
                        columnNames = {"document_id", "table_key"}
                )
        },
        indexes = {
                @Index(name = "idx_document_tables_widget", columnList = "widget_config_id"),
                @Index(name = "idx_document_tables_document_order", columnList = "document_id, order_index"),
                @Index(name = "idx_document_tables_section_key", columnList = "widget_config_id, section_key"),
                @Index(name = "idx_document_tables_table_key", columnList = "widget_config_id, table_key")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@SQLRestriction("deleted_at IS NULL")
public class DocumentTable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "widget_config_id", nullable = false)
    private WidgetConfig widgetConfig;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "section_id")
    private DocumentSection section;

    @Column(name = "table_key", nullable = false, length = 64)
    private String tableKey;

    @Column(name = "section_key", length = 64)
    private String sectionKey;

    @Column(name = "title", length = 500)
    private String title;

    @Column(name = "page_start")
    private Integer pageStart;

    @Column(name = "page_end")
    private Integer pageEnd;

    @Column(name = "order_index", nullable = false)
    private Integer orderIndex;

    @Column(name = "markdown_content", nullable = false, columnDefinition = "LONGTEXT")
    private String markdownContent;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "json_content", columnDefinition = "json")
    private List<Map<String, Object>> jsonContent;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}