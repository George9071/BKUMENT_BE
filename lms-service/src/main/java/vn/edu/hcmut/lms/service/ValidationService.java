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

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class ValidationService {
    EnrollmentRepository enrollmentRepository;
    ClassRoomRepository classRoomRepository;

    public void validateBusySchedule(String userId, ClassRoom proposedClass) {
        List<ClassStatus> activeStatuses = List.of(ClassStatus.ENROLLING, ClassStatus.ONGOING);

        List<ClassRoom> teachingClasses = classRoomRepository
                .findActiveClassesByTutor(userId, activeStatuses);
        List<ClassRoom> studyingClasses = enrollmentRepository
                .findActiveClassesByStudent(userId, EnrollmentStatus.APPROVED, activeStatuses);

        checkConflicts(proposedClass, teachingClasses, "lịch dạy");
        checkConflicts(proposedClass, studyingClasses, "lịch học");
    }

    private void checkConflicts(ClassRoom proposed, List<ClassRoom> existingClasses, String role) {
        for (ClassRoom existing : existingClasses) {
            if (existing.getId().equals(proposed.getId())) continue;

            findConflictingSlot(proposed, existing).ifPresent(detail -> {
                throw new AppException(ErrorCode.SCHEDULE_CONFLICT,
                        buildConflictDetail(existing, role, detail).toMessage());
            });
        }
    }

    Optional<ClassSchedule[]> findConflictingSlot(ClassRoom proposed, ClassRoom existing) {
        boolean dateOverlap =
                !proposed.getStartDate().isAfter(existing.getEndDate()) &&
                        !proposed.getEndDate().isBefore(existing.getStartDate());

        if (!dateOverlap) return Optional.empty();

        for (ClassSchedule newSch : proposed.getSchedules()) {
            for (ClassSchedule existingSch : existing.getSchedules()) {
                if (isTimeSlotConflict(newSch, existingSch)) {
                    return Optional.of(new ClassSchedule[]{newSch, existingSch});
                }
            }
        }
        return Optional.empty();
    }

    private boolean isTimeSlotConflict(ClassSchedule s1, ClassSchedule s2) {
        if (s1.getDayOfWeek() != s2.getDayOfWeek()) return false;
        return s1.getStartTime().isBefore(s2.getEndTime()) &&
                s1.getEndTime().isAfter(s2.getStartTime());
    }

    private ScheduleConflictDetail buildConflictDetail(
            ClassRoom existing, String role, ClassSchedule[] slots) {

        ClassSchedule existingSlot = slots[1];

        LocalTime overlapStart = existingSlot.getStartTime()
                .isAfter(slots[0].getStartTime())
                ? existingSlot.getStartTime() : slots[0].getStartTime();
        LocalTime overlapEnd = existingSlot.getEndTime()
                .isBefore(slots[0].getEndTime())
                ? existingSlot.getEndTime() : slots[0].getEndTime();

        return new ScheduleConflictDetail(
                existing.getId(),
                existing.getName(),
                role,
                existing.getStartDate(),
                existing.getEndDate(),
                existingSlot.getDayOfWeek(),
                overlapStart,
                overlapEnd
        );
    }
}
