package vn.edu.hcmut.lms.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;

@Entity
@Table(name = "class_notifications")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ClassNotification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // FORMAT: #12345
    Long id;

    String title;

    @Column(columnDefinition = "TEXT")
    String message;

    LocalDateTime sentAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "class_id")
    ClassRoom classRoom;
}
