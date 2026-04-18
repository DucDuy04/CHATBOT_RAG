package KLTN.RAG_CHATBOT_BE.domain.chat;

import jakarta.persistence.*;
import lombok.*;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

import KLTN.RAG_CHATBOT_BE.domain.widget.WidgetConfig;

@Entity
@Table(name = "chat_sessions")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
@SQLRestriction("deleted_at IS NULL")
public class ChatSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "widget_config_id", nullable = false)
    private WidgetConfig widgetConfig;

    @Column(name = "session_key", unique = true, nullable = false, updatable = false)
    private UUID sessionKey; // Được gửi từ trình duyệt người dùng để phân biệt các người chat ẩn danh

    private String title;

    @Column(name = "widget_origin", nullable = false)
    private String widgetOrigin; // Tên miền gốc gửi request chat đến (dùng lưu vết)

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}