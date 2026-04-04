package vn.edu.hcmut.communication.notification.event;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TutorApplicationEvent {
    String profileId;
    String action;
    String reason;
}
