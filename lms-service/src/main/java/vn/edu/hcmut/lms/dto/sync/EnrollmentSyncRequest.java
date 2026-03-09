package vn.edu.hcmut.lms.dto.sync;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnrollmentSyncRequest {
    private String studentId;
    private String classId;
}
