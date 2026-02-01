package vn.edu.hcmut.lms.dto.request;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ClassRoomCreationRequest {
    String name;
    String description;
    LocalDate startDate;
    LocalDate endDate;
    String schedule;
    String topicId;
}
