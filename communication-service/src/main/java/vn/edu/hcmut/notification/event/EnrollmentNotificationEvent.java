package vn.edu.hcmut.notification.event;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class EnrollmentNotificationEvent {
    String action;

    String classId;
    String className;

    String studentId;
    String studentName;

    String tutorId;

    Instant timestamp;
}
