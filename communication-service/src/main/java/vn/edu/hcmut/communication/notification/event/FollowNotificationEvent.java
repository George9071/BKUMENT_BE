package vn.edu.hcmut.communication.notification.event;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class FollowNotificationEvent {
    String followerId;
    String followerName;
    String followeeId;
    String action;
}
