package vn.edu.hcmut.lms.dto.request;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationRequest {
    String title;
    String message;
}
