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
@Table(name = "messages")
public class Message {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    String id;

    @ManyToOne
    @JoinColumn(name = "conversation_id")
    Conversation conversation;

    @Column(name = "sender_id")
    String senderId;

    @Column(columnDefinition = "TEXT")
    String content;

    LocalDateTime sentAt;
}
