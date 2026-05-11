package KLTN.RAG_CHATBOT_BE.domain.settings;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Singleton application profile (no multi-user auth). Fixed {@link #SINGLETON_ID}.
 */
@Entity
@Table(name = "settings_profiles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SettingsProfile {

    public static final UUID SINGLETON_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "full_name", nullable = false, length = 255)
    private String fullName;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(nullable = false, length = 8)
    @Builder.Default
    private String language = "vi";

    @Column(name = "notify_embedding_failed", nullable = false)
    @Builder.Default
    private boolean notifyEmbeddingFailed = false;

    @Column(name = "notify_daily_summary", nullable = false)
    @Builder.Default
    private boolean notifyDailySummary = false;

    @Column(name = "notify_new_feedback", nullable = false)
    @Builder.Default
    private boolean notifyNewFeedback = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
