package KLTN.RAG_CHATBOT_BE.domain.chat;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "chat_messages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String sessionId; // ID phiên chat (tạo từ FE)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MessageRole role; // USER hoặc ASSISTANT

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content; // Nội dung tin nhắn

    @Column(columnDefinition = "TEXT")
    private String sources; // JSON string chứa nguồn tài liệu

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public enum MessageRole {
        USER,
        ASSISTANT
    }
}