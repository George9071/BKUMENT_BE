package vn.edu.hcmut.lms.service;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import vn.edu.hcmut.lms.constant.ClassStatus;
import vn.edu.hcmut.lms.constant.EnrollmentStatus;
import vn.edu.hcmut.lms.dto.response.ScheduleConflictDetail;
import vn.edu.hcmut.lms.entity.ClassRoom;
import vn.edu.hcmut.lms.entity.ClassSchedule;
import vn.edu.hcmut.lms.exception.AppException;
import vn.edu.hcmut.lms.exception.ErrorCode;
import vn.edu.hcmut.lms.repository.ClassRoomRepository;
import vn.edu.hcmut.lms.repository.EnrollmentRepository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;


/*
* validate_class_timing     check if start_date, end_date, and schedules are valid.
* validate_busy_schedule    retrieve the user classes that are currently teaching and learning.
* check_conflicts           compare the new class with each existing class.
* find_conflicting_slot     check for date overlap and time slot overlap.
* build_conflict_detail     generate a detailed error message if there is a duplicate.
* */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class ValidationService {
    EnrollmentRepository enrollmentRepository;
    ClassRoomRepository classRoomRepository;

    public void validateClassTiming(ClassRoom classRoom) {
        validateDateRange(classRoom.getStartDate(), classRoom.getEndDate());
        validateSchedules(classRoom.getSchedules());
    }

    public void validateBusySchedule(String userId, ClassRoom proposedClass) {
        List<ClassStatus> activeStatuses = List.of(
                ClassStatus.ENROLLING,
                ClassStatus.ONGOING
        );

        List<ClassRoom> teachingClasses = classRoomRepository
                .findActiveClassesByTutor(userId, activeStatuses);

        List<ClassRoom> studyingClasses = enrollmentRepository
                .findActiveClassesByStudent(
                        userId,
                        EnrollmentStatus.APPROVED,
                        activeStatuses
                );

        validateNoConflictWithExistingClasses(
                proposedClass,
                teachingClasses,
                "lịch dạy"
        );

        validateNoConflictWithExistingClasses(
                proposedClass,
                studyingClasses,
                "lịch học"
        );
    }

    private void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null || startDate.isAfter(endDate)) {
            throw new AppException(ErrorCode.INVALID_FORMAT);
        }
    }

    private void validateSchedules(List<ClassSchedule> schedules) {
        if (schedules == null || schedules.isEmpty()) {
            throw new AppException(ErrorCode.INVALID_FORMAT);
        }

        schedules.forEach(this::validateSchedule);
    }

    private void validateSchedule(ClassSchedule schedule) {
        if (schedule == null
                || schedule.getDayOfWeek() == null
                || schedule.getStartTime() == null
                || schedule.getEndTime() == null
                || !schedule.getStartTime().isBefore(schedule.getEndTime())) {
            throw new AppException(ErrorCode.INVALID_FORMAT);
        }
    }

    private void validateNoConflictWithExistingClasses(
            ClassRoom proposedClass,
            List<ClassRoom> existingClasses,
            String role
    ) {
        for (ClassRoom existingClass : existingClasses) {
            if (isSameClass(proposedClass, existingClass)) continue;

            Optional<ScheduleConflictSlot> conflict =
                    findScheduleConflict(proposedClass, existingClass);

            if (conflict.isPresent()) {
                ScheduleConflictDetail detail = buildConflictDetail(
                        existingClass,
                        role,
                        conflict.get()
                );

                throw new AppException(
                        ErrorCode.SCHEDULE_CONFLICT,
                        detail.toMessage()
                );
            }
        }
    }

    private boolean isSameClass(ClassRoom proposedClass, ClassRoom existingClass) {
        return Objects.equals(proposedClass.getId(), existingClass.getId());
    }

    private Optional<ScheduleConflictSlot> findScheduleConflict(
            ClassRoom proposedClass,
            ClassRoom existingClass
    ) {
        if (!hasDateOverlap(proposedClass, existingClass)) return Optional.empty();

        for (ClassSchedule proposedSlot : proposedClass.getSchedules()) {
            for (ClassSchedule existingSlot : existingClass.getSchedules()) {
                if (hasScheduleOverlap(proposedSlot, existingSlot)) {
                    return Optional.of(new ScheduleConflictSlot(
                            proposedSlot,
                            existingSlot
                    ));
                }
            }
        }

        return Optional.empty();
    }

    private boolean hasDateOverlap(ClassRoom proposedClass, ClassRoom existingClass) {
        return !proposedClass.getStartDate().isAfter(existingClass.getEndDate())
                && !proposedClass.getEndDate().isBefore(existingClass.getStartDate());
    }

    private boolean hasScheduleOverlap(
            ClassSchedule proposedSlot,
            ClassSchedule existingSlot
    ) {
        if (proposedSlot.getDayOfWeek() != existingSlot.getDayOfWeek()) return false;

        return hasTimeOverlap(
                proposedSlot.getStartTime(),
                proposedSlot.getEndTime(),
                existingSlot.getStartTime(),
                existingSlot.getEndTime()
        );
    }

    private boolean hasTimeOverlap(
            LocalTime start1,
            LocalTime end1,
            LocalTime start2,
            LocalTime end2
    ) {
        return start1.isBefore(end2) && end1.isAfter(start2);
    }

    private ScheduleConflictDetail buildConflictDetail(
            ClassRoom existingClass,
            String role,
            ScheduleConflictSlot conflict
    ) {
        ClassSchedule proposedSlot = conflict.proposedSlot();
        ClassSchedule existingSlot = conflict.existingSlot();

        LocalTime overlapStart = max(proposedSlot.getStartTime(), existingSlot.getStartTime());
        LocalTime overlapEnd = min(proposedSlot.getEndTime(), existingSlot.getEndTime());

        return new ScheduleConflictDetail(
                existingClass.getId(),
                existingClass.getName(),
                role,
                existingClass.getStartDate(),
                existingClass.getEndDate(),
                existingSlot.getDayOfWeek(),
                overlapStart,
                overlapEnd
        );
    }

    private LocalTime max(LocalTime first, LocalTime second) {
        return first.isAfter(second) ? first : second;
    }

    private LocalTime min(LocalTime first, LocalTime second) {
        return first.isBefore(second) ? first : second;
    }

    private record ScheduleConflictSlot(
            ClassSchedule proposedSlot,
            ClassSchedule existingSlot
    ) { }
}
