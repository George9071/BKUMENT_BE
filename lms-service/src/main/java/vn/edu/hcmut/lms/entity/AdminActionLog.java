package vn.edu.hcmut.lms.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import vn.edu.hcmut.lms.constant.AdminActionType;

import java.time.Instant;
import java.util.Map;

@Entity
@Table(
        name = "admin_action_log",
        indexes = {
                @Index(name = "idx_admin_log_actor_at", columnList = "actor_id, created_at"),
                @Index(name = "idx_admin_log_target", columnList = "target_type, target_id"),
                @Index(name = "idx_admin_log_action_at", columnList = "action, created_at")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AdminActionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    String id;

    /** Account ID of admin/moderator. */
    @Column(name = "actor_id", nullable = false, length = 64)
    String actorId;

    /** "ADMIN" or "MODERATOR". */
    @Column(name = "actor_role", nullable = false, length = 32)
    String actorRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 32)
    AdminActionType action;

    /** Type of entity affected: "SUBJECT" | "TOPIC" | "SUGGESTION". */
    @Column(name = "target_type", nullable = false, length = 32)
    String targetType;

    /** The ID of the affected entity (after creation, or before deletion). */
    @Column(name = "target_id", length = 64)
    String targetId;

    /** The ID of the original suggestion if the action originates from proposal approval. */
    @Column(name = "source_suggestion_id", length = 64)
    String sourceSuggestionId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "before_snapshot", columnDefinition = "jsonb")
    Map<String, Object> beforeSnapshot;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "after_snapshot", columnDefinition = "jsonb")
    Map<String, Object> afterSnapshot;

    /** Reason/note from the actor (required for DELETE | REJECT). */
    @Column(name = "note", length = 2000)
    String note;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    Instant createdAt;
}
