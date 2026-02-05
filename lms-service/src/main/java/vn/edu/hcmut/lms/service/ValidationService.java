package vn.edu.hcmut.lms.service;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import vn.edu.hcmut.lms.constant.ClassStatus;
import vn.edu.hcmut.lms.constant.EnrollmentStatus;
import vn.edu.hcmut.lms.entity.ClassRoom;
import vn.edu.hcmut.lms.entity.ClassSchedule;
import vn.edu.hcmut.lms.exception.AppException;
import vn.edu.hcmut.lms.exception.ErrorCode;
import vn.edu.hcmut.lms.repository.ClassRoomRepository;
import vn.edu.hcmut.lms.repository.EnrollmentRepository;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class ValidationService {
    EnrollmentRepository enrollmentRepository;
    ClassRoomRepository classRoomRepository;

    public boolean isScheduleConflict(ClassRoom newClass, ClassRoom existingClass) {
        boolean isDateOverlap = !newClass.getStartDate().isAfter(existingClass.getEndDate()) &&
                !newClass.getEndDate().isBefore(existingClass.getStartDate());

        if (!isDateOverlap) return false;

        for (ClassSchedule newSch : newClass.getSchedules()) {
            for (ClassSchedule existingSch : existingClass.getSchedules()) {
                if (isTimeSlotConflict(newSch, existingSch)) return true;
            }
        }

        return false;
    }

    private boolean isTimeSlotConflict(ClassSchedule sch1, ClassSchedule sch2) {
        if (sch1.getDayOfWeek() != sch2.getDayOfWeek()) return false;

        return sch1.getStartTime().isBefore(sch2.getEndTime()) &&
                sch1.getEndTime().isAfter(sch2.getStartTime());
    }

    public void validateBusySchedule(String userId, ClassRoom proposedClass) {
        List<ClassStatus> activeStatuses = List.of(ClassStatus.ENROLLING, ClassStatus.ONGOING);

        List<ClassRoom> teachingClasses = classRoomRepository.findActiveClassesByTutor(userId, activeStatuses);
        List<ClassRoom> busyClasses = new ArrayList<>(teachingClasses);

        List<ClassRoom> studyingClasses = enrollmentRepository.findActiveClassesByStudent(
                userId,
                EnrollmentStatus.APPROVED,
                activeStatuses
        );
        busyClasses.addAll(studyingClasses);

        for (ClassRoom existingClass : busyClasses) {
            if (isScheduleConflict(proposedClass, existingClass)) {
                String role = teachingClasses.contains(existingClass) ? "lịch dạy" : "lịch học";

                throw new AppException(ErrorCode.SCHEDULE_CONFLICT,
                        String.format("Bị trùng với %s lớp '%s' (%s)",
                                role, existingClass.getName(), existingClass.getId()));
            }
        }
    }
}
