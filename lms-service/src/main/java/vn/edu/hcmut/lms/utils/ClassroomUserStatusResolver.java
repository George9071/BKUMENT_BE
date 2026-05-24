package vn.edu.hcmut.lms.utils;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.edu.hcmut.lms.entity.ClassRoom;
import vn.edu.hcmut.lms.repository.EnrollmentRepository;

/**
 * Resolves the current user's relationship status with a ClassRoom.

 * Possible return values:
 * "OWNER" – the user is the tutor of this class
 * "NONE" – the user has no relationship with this class
 * "PENDING" – the user has a pending enrollment request
 * "APPROVED" – the user is an enrolled student
 * "REJECTED" – the user's enrollment was rejected
 */

@Component
@RequiredArgsConstructor
public class ClassroomUserStatusResolver {

    private final EnrollmentRepository enrollmentRepository;

    public String resolve(ClassRoom classroom, String userId) {
        if (userId == null) return "NONE";
        if (isOwner(classroom, userId)) return "OWNER";

        return enrollmentRepository
                .findByClassRoomIdAndStudentProfileId(classroom.getId(), userId)
                .map(e -> e.getStatus().name())
                .orElse("NONE");
    }

    public Map<String, String> resolveBatch(List<ClassRoom> classrooms, String userId) {
        List<String> classIds = classrooms.stream()
                .map(ClassRoom::getId)
                .toList();

        // Pre-fetch all enrollments for this user in one query
        Map<String, String> enrollmentStatusMap = (userId != null && !classIds.isEmpty())
                ? enrollmentRepository
                .findByStudentProfileIdAndClassRoomIdIn(userId, classIds)
                .stream()
                .collect(Collectors.toMap(
                        e -> e.getClassRoom().getId(),
                        e -> e.getStatus().name()))
                : Map.of();

        return classrooms.stream().collect(Collectors.toMap(
                ClassRoom::getId,
                c -> resolveFromMap(c, userId, enrollmentStatusMap)
        ));
    }

    private String resolveFromMap(ClassRoom classroom, String userId,
                                  Map<String, String> enrollmentStatusMap) {
        if (userId == null) return "NONE";
        if (isOwner(classroom, userId)) return "OWNER";
        return enrollmentStatusMap.getOrDefault(classroom.getId(), "NONE");
    }

    private boolean isOwner(ClassRoom classroom, String userId) {
        if (classroom.getTutor() == null) return false;
        assert classroom.getTutor().getId() != null;
        return classroom.getTutor().getId().equals(userId);
    }
}
