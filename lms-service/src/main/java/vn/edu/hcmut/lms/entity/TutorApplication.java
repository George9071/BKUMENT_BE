package vn.edu.hcmut.lms.entity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CreationTimestamp;
import vn.edu.hcmut.lms.constant.ApplicationStatus;
import vn.edu.hcmut.lms.converter.StringListConverter;

import java.time.Instant;
import java.util.List;


@Entity
@Table(name = "tutor_applications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TutorApplication {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    String id;

    @Column(nullable = false, updatable = false)
    String profileId;

    @Column(columnDefinition = "TEXT", nullable = false)
    String name;

    String avatar;

    @Column(columnDefinition = "TEXT")
    String introduction;

    @Column(columnDefinition = "TEXT")
    String experience;

    @Column(name = "cv_url")
    String cvUrl;

    @Convert(converter = StringListConverter.class)
    @Column(name = "subject_ids")
    List<String> subjectIds;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    ApplicationStatus status;

    @Column(columnDefinition = "TEXT")
    String rejectionReason;

    String reviewedBy;

    Instant reviewedAt;

    @CreationTimestamp
    @Column(updatable = false)
    Instant createdAt;
}
