package vn.edu.hcmut.lms.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;
import vn.edu.hcmut.lms.constant.EnrollmentStatus;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class EnrollmentResponse {
    String id;
    String studentId; // Profile ID
    String studentName;
    String studentEmail;
    String studentAvatar;

    LocalDateTime enrolledAt;
    EnrollmentStatus status;
}
