package KLTN.RAG_CHATBOT_BE.domain.widget;

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
@Table(name = "widget_configs")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
@SQLRestriction("deleted_at IS NULL")
public class WidgetConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String name;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "allowed_origin", nullable = false)
    private List<String> allowedOrigin;

    // @Column(name = "system_prompt", columnDefinition = "TEXT")
    // private String systemPrompt;

    @Column(name = "api_key", unique = true, nullable = false, updatable = false)
    private UUID apiKey; 

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ui_config")
    private Map<String, Object> uiConfig;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}