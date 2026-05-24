package vn.edu.hcmut.lms.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;
import vn.edu.hcmut.lms.constant.ClassStatus;
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
public class ClassRoomUpdateRequest {

    String name;

    @Size(max = 5000, message = "INVALID_FORMAT")
    String description;

    LocalDate startDate;

    LocalDate endDate;

    /**
     * Set a new topic for this classroom.
     * Mutually exclusive with clearTopic — if both are set, clearTopic takes precedence.
     */
    String topicId;

    /**
     * Set to true to explicitly remove the current topic from this classroom.
     * If null or false, the existing topic is kept when topicId is also null.
     */
    Boolean clearTopic;

    /**
     * Allowed transitions (enforced in service):
     *   ENROLLING → ONGOING | CANCELLED
     *   ONGOING   -> FINISHED | CANCELLED
     *   FINISHED / CANCELLED -> (no transition allowed)
     */
    ClassStatus status;

    String location;

    LearningFormat format;
    String coverImageUrl;

    /**
     * When provided, replaces ALL existing schedules entirely.
     * If null, existing schedules are left unchanged.
     */
    @Valid
    List<ScheduleRequest> schedules;

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
