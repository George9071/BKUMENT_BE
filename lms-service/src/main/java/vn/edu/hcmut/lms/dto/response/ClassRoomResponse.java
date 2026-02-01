package vn.edu.hcmut.lms.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;
import vn.edu.hcmut.lms.constant.ClassStatus;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ClassRoomResponse {
    String id;
    String name;
    String description;

    LocalDate startDate;
    LocalDate endDate;
    String schedule;

    ClassStatus status;

    String tutorId;
    String tutorName;
    String tutorAvatar;

    String topicName;
    String subjectName;
}
