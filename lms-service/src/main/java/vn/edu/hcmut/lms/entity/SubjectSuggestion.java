package vn.edu.hcmut.lms.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import vn.edu.hcmut.lms.constant.SuggestionStatus;
import vn.edu.hcmut.lms.constant.SuggestionType;

import java.time.Instant;

@Entity
@Table(
        name = "subject_suggestion",
        indexes = {
                @Index(name = "idx_suggestion_reporter_status", columnList = "reporter_id, status"),
                @Index(name = "idx_suggestion_status_created", columnList = "status, created_at")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SubjectSuggestion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    String id;

    @Column(name = "reporter_id", nullable = false, length = 64)
    String reporterId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 16)
    SuggestionType type;

    @Column(name = "proposed_name", nullable = false, length = 255)
    String proposedName;

    @Column(name = "parent_subject_id", length = 64)
    String parentSubjectId;

    @Column(name = "reason", length = 2000)
    String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    @Builder.Default
    SuggestionStatus status = SuggestionStatus.PENDING;

    @Column(name = "reviewer_id", length = 64)
    String reviewerId;

    @Column(name = "rejection_reason", length = 2000)
    String rejectionReason;

    @Column(name = "created_resource_id", length = 64)
    String createdResourceId;

    @Column(name = "reviewed_at")
    Instant reviewedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    Instant updatedAt;
}
