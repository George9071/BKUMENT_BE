package vn.edu.hcmut.social.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;

import lombok.*;
import lombok.experimental.FieldDefaults;
import vn.edu.hcmut.social.enums.ReportReason;
import vn.edu.hcmut.social.enums.ReportStatus;
import vn.edu.hcmut.social.enums.ReportType;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Entity
@Table(name = "report")
public class Report {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    String id;

    @Column(name = "resolver_id")
    String resolverId;

    @Column(name = "reporter_id")
    String reporterId;

    @Column(name = "target_id")
    String targetId;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    ReportStatus status = ReportStatus.PENDING;

    @Enumerated(EnumType.STRING)
    ReportType type;

    @Enumerated(EnumType.STRING)
    ReportReason reason;

    @Column(columnDefinition = "TEXT")
    String detail;

    @Column(name = "is_deleted")
    @Builder.Default
    boolean deleted = false;

    @Column(name = "created_at", updatable = false)
    LocalDateTime createdAt;

    @Column(name = "updated_at")
    LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
