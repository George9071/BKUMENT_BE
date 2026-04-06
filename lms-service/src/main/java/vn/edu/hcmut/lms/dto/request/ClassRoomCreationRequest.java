package vn.edu.hcmut.lms.dto.request;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.Valid;
import lombok.*;
import lombok.experimental.FieldDefaults;
import vn.edu.hcmut.lms.constant.LearningFormat;

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
    @NotBlank(message = "REQUIRED_FIELD")
    String name;

    String description;

    @NotNull(message = "REQUIRED_FIELD")
    @FutureOrPresent(message = "INVALID_FORMAT")
    LocalDate startDate;

    @NotNull(message = "REQUIRED_FIELD")
    @FutureOrPresent(message = "INVALID_FORMAT")
    LocalDate endDate;

    @NotNull(message = "REQUIRED_FIELD")
    String topicId;

    @NotEmpty(message = "REQUIRED_FIELD")
    @Valid
    List<ScheduleRequest> schedules;

    String location;
    String coverImageUrl;

    LearningFormat format;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScheduleRequest {
        @NotNull(message = "REQUIRED_FIELD")
        DayOfWeek dayOfWeek; // MONDAY, TUESDAY...

        @NotNull(message = "REQUIRED_FIELD")
        LocalTime startTime; // 19:00:00

        @NotNull(message = "REQUIRED_FIELD")
        LocalTime endTime;   // 21:00:00
    }
}
