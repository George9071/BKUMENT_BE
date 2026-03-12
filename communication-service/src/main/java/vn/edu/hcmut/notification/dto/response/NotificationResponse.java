package vn.edu.hcmut.notification.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class NotificationResponse {
    String id;
    String type;
    String title;
    String message;
    Instant timestamp;
    boolean isRead;
    Map<String, Object> metadata;
}
