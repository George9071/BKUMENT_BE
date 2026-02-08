package vn.edu.hcmut.lms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.DayOfWeek;
import java.time.LocalTime;

@Entity
@Table(name = "class_schedules")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClassSchedule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @ManyToOne
    @JoinColumn(name = "class_id")
    ClassRoom classRoom;

    @Enumerated(EnumType.STRING)
    DayOfWeek dayOfWeek;

    LocalTime startTime;
    LocalTime endTime;
}
