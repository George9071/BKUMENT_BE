package vn.edu.hcmut.lms.dto.response;

import lombok.*;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationResponse {
    String id;
    String title;
    String message;
    LocalDateTime sentAt;
    String classId;
    String className;
}
