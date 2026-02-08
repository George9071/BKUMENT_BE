package vn.edu.hcmut.lms.dto.request;

import lombok.*;
import lombok.experimental.FieldDefaults;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

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
    String topicId;

    List<ScheduleRequest> schedules;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScheduleRequest {
        DayOfWeek dayOfWeek; // MONDAY, TUESDAY...
        LocalTime startTime; // 19:00:00
        LocalTime endTime;   // 21:00:00
    }
}
