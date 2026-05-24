package vn.edu.hcmut.lms.entity;

import lombok.*;
import lombok.experimental.FieldDefaults;
import jakarta.persistence.*;
import vn.edu.hcmut.lms.constant.ClassStatus;
import vn.edu.hcmut.lms.constant.LearningFormat;

import java.time.LocalDate;
import java.util.List;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Table(name = "class_room")
public class ClassRoom {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    String id;
    String name;

    @Column(columnDefinition = "TEXT")
    String description;


    String coverImageUrl;

    LocalDate startDate;
    LocalDate endDate;

    String location;

    @Enumerated(EnumType.STRING)
    LearningFormat format;

    @OneToMany(mappedBy = "classRoom", cascade = CascadeType.ALL, orphanRemoval = true)
    List<ClassSchedule> schedules;

    @Enumerated(EnumType.STRING)
    ClassStatus status;

    @Builder.Default
    @Column(name = "average_rating")
    Double averageRating = 0.0;

    @Builder.Default
    @Column(name = "rating_count")
    Integer ratingCount = 0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tutor_id", nullable = false)
    Tutor tutor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "topic_id")
    Topic topic;
}


