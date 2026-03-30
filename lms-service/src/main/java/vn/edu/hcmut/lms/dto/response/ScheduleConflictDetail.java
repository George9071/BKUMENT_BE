package vn.edu.hcmut.lms.dto.response;

import lombok.Value;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;

@Value
public class ScheduleConflictDetail {
    String conflictingClassId;
    String conflictingClassName;
    String type;
    LocalDate startDate;
    LocalDate endDate;
    DayOfWeek dayOfWeek;
    LocalTime conflictStart;
    LocalTime conflictEnd;

    public String toMessage() {
        return String.format(
                "Trùng %s lớp '%s' (ID: %s) | Thời gian: %s - %s | Thứ %s: %s - %s",
                type,
                conflictingClassName,
                conflictingClassId,
                startDate,
                endDate,
                dayOfWeek.getValue() == 1 ? "CN" : String.valueOf(dayOfWeek.getValue()),
                conflictStart,
                conflictEnd
        );
    }
}
