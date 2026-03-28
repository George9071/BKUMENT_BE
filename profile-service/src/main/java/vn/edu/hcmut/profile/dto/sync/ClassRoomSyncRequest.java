package vn.edu.hcmut.profile.dto.sync;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ClassRoomSyncRequest {
    String id;
    String name;
    String status;
    String format;
    String topicId;
}
