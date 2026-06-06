package KLTN.RAG_CHATBOT_BE.domain.document;

import KLTN.RAG_CHATBOT_BE.domain.widget.WidgetConfig;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "document_sections",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_document_sections_doc_section_key",
                        columnNames = {"document_id", "section_key"}
                )
        },
        indexes = {
                @Index(name = "idx_document_sections_widget", columnList = "widget_config_id"),
                @Index(name = "idx_document_sections_document_order", columnList = "document_id, order_index"),
                @Index(name = "idx_document_sections_widget_section_key", columnList = "widget_config_id, section_key")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@SQLRestriction("deleted_at IS NULL")
public class DocumentSection {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "widget_config_id", nullable = false)
    private WidgetConfig widgetConfig;

    @Column(name = "section_key", nullable = false, length = 64)
    private String sectionKey;

    @Column(name = "parent_section_key", length = 64)
    private String parentSectionKey;

    @Column(name = "title", nullable = false, length = 500)
    private String title;

    @Column(name = "heading_path_text", length = 1000)
    private String headingPathText;

    @Column(name = "page_start")
    private Integer pageStart;

    @Column(name = "page_end")
    private Integer pageEnd;

    @Column(name = "order_index", nullable = false)
    private Integer orderIndex;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}