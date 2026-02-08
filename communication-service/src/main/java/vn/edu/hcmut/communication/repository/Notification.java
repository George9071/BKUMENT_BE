package vn.edu.hcmut.communication.repository;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Table(name = "notifications")
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    String id;

    @Column(name = "recipient_id")
    String recipientId; // Người nhận

    String content;
    String type; // SYSTEM, CLASS...
    boolean isRead;
    LocalDateTime createdAt;
}
