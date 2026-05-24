package vn.edu.hcmut.profile.entity.jpa;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "outbox_events")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutboxEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    String id;
    String aggregateType; // "PROFILE"
    String aggregateId;   // ProfileID
    String eventType;     // "PROFILE_CREATED", "PROFILE_DELETED"
    String payload;
    boolean processed;
    LocalDateTime createdAt;
}
