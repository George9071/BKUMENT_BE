package vn.edu.hcmut.lms.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;
import vn.edu.hcmut.lms.constant.ClassStatus;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

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
    List<ScheduleResponse> schedules;

    ClassStatus status;

    String tutorId;
    String tutorName;
    String tutorAvatar;

    String topicName;
    String subjectName;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScheduleResponse {
        DayOfWeek dayOfWeek;
        LocalTime startTime;
        LocalTime endTime;
    }
}
