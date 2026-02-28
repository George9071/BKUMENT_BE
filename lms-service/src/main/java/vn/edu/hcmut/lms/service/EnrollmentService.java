package vn.edu.hcmut.lms.service;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmut.lms.constant.ClassStatus;
import vn.edu.hcmut.lms.constant.EnrollmentStatus;
import vn.edu.hcmut.lms.dto.response.EnrollmentResponse;
import vn.edu.hcmut.lms.dto.response.ProfileResponse;
import vn.edu.hcmut.lms.entity.ClassRoom;
import vn.edu.hcmut.lms.entity.Enrollment;
import vn.edu.hcmut.lms.exception.*;
import vn.edu.hcmut.lms.mapper.EnrollmentMapper;
import vn.edu.hcmut.lms.repository.ClassRoomRepository;
import vn.edu.hcmut.lms.repository.EnrollmentRepository;
import vn.edu.hcmut.lms.repository.httpclient.ProfileClient;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class EnrollmentService {

    ValidationService validationService;
    GraphSyncService graphSyncService;
    EnrollmentRepository enrollmentRepository;
    ClassRoomRepository classRoomRepository;
    ProfileClient profileClient;
    EnrollmentMapper enrollmentMapper;

    @Transactional
    public EnrollmentResponse enrollClass(String classId) {
        String userId = getProfileIdFromToken();

        var userProfile = profileClient.getProfile(userId).getResult();

        ClassRoom classRoom = classRoomRepository.findById(classId)
                .orElseThrow(() -> new AppException(ErrorCode.CLASS_NOT_FOUND));

        if (classRoom.getStatus() != ClassStatus.ENROLLING) {
            throw new AppException(ErrorCode.CLASS_NOT_AVAILABLE);
        }

        if (classRoom.getTutor() != null && classRoom.getTutor().getId().equals(userId)) {
            throw new AppException(ErrorCode.CANNOT_ENROLL_OWN_CLASS);
        }

        if (enrollmentRepository.existsByClassRoomIdAndStudentProfileId(classId, userId)) {
            throw new AppException(ErrorCode.ALREADY_ENROLLED);
        }

        validationService.validateBusySchedule(userId, classRoom);

        Enrollment enrollment = Enrollment.builder()
                .classRoom(classRoom)
                .studentProfileId(userId)
                .enrolledAt(LocalDateTime.now())
                .status(EnrollmentStatus.PENDING)
                .build();

        enrollment = enrollmentRepository.save(enrollment);

        String topicId = classRoom.getTopic() != null ? classRoom.getTopic().getId() : null;
        graphSyncService.handleEnrollmentEvent(userId, classId, topicId);

        return enrollmentMapper.toResponse(enrollment, userProfile);
    }

    @Transactional
    public void approveEnrollment(String enrollmentId, boolean isApproved) {
        String tutorId = getProfileIdFromToken();

        Enrollment enrollment = enrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new AppException(ErrorCode.ENROLLMENT_NOT_FOUND));

        if (!enrollment.getClassRoom().getTutor().getId().equals(tutorId)) {
            throw new AppException(ErrorCode.UNAUTHORIZED_ACTION);
        }

        enrollment.setStatus(isApproved ? EnrollmentStatus.APPROVED : EnrollmentStatus.REJECTED);
        enrollmentRepository.save(enrollment);
    }

    public List<EnrollmentResponse> getPendingRequestsOfClass(String classId) {
        String currentUserId = getProfileIdFromToken();

        ClassRoom classRoom = classRoomRepository.findById(classId)
                .orElseThrow(() -> new AppException(ErrorCode.CLASS_NOT_FOUND));

<<<<<<< Updated upstream
        if (!classRoom.getTutor().getId().equals(currentUserId))
            throw new AppException(ErrorCode.UNAUTHORIZED_ACCESS);

        List<Enrollment> enrollments =
                enrollmentRepository.findByClassRoomIdAndStatus(classId, EnrollmentStatus.PENDING);
=======
        List<Enrollment> enrollments = enrollmentRepository.findByClassRoomId(classId);
>>>>>>> Stashed changes

        if (enrollments.isEmpty()) return Collections.emptyList();

        return toEnrollmentResponses(enrollments);
    }

<<<<<<< Updated upstream
    public List<EnrollmentResponse> getClassMembers(String classId) {
        List<Enrollment> enrollments =
                enrollmentRepository.findByClassRoomIdAndStatus(classId, EnrollmentStatus.APPROVED);

        if (enrollments.isEmpty()) return new ArrayList<>();

        return toEnrollmentResponses(enrollments);
=======
        // B3: Map dữ liệu
        final var profilesFinal = profileMap;
        return enrollments.stream()
                .map(enrollment -> enrollmentMapper.toResponse(
                        enrollment,
                        profilesFinal.get(enrollment.getStudentProfileId())))
                .toList();
>>>>>>> Stashed changes
    }

    @Transactional
    public void removeStudent(String enrollmentId) {
        String tutorId = getProfileIdFromToken();

        Enrollment enrollment = enrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new AppException(ErrorCode.ENROLLMENT_NOT_FOUND));

        if (!enrollment.getClassRoom().getTutor().getId().equals(tutorId)) {
            throw new AppException(ErrorCode.UNAUTHORIZED_ACTION);
        }

        enrollmentRepository.delete(enrollment);
    }

    // --- Helper Methods ---
    private String getProfileIdFromToken() {
        var jwt = (Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return jwt.getClaimAsString("profile_id");
    }

    private List<EnrollmentResponse> toEnrollmentResponses(List<Enrollment> enrollments) {
        Set<String> studentIds = enrollments.stream()
                .map(Enrollment::getStudentProfileId)
                .collect(Collectors.toSet());

        var profiles = profileClient.getProfiles(new ArrayList<>(studentIds)).getResult();

        Map<String, ProfileResponse> profileMap = profiles.stream()
                .collect(Collectors.toMap(ProfileResponse::getId, Function.identity()));

        return enrollmentMapper.toResponseList(enrollments, profileMap);
    }
}
