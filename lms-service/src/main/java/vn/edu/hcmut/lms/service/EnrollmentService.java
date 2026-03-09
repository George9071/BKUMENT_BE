package vn.edu.hcmut.lms.service;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmut.lms.constant.ClassStatus;
import vn.edu.hcmut.lms.constant.EnrollmentStatus;
import vn.edu.hcmut.lms.dto.response.ClassRoomResponse;
import vn.edu.hcmut.lms.dto.response.EnrollmentResponse;
import vn.edu.hcmut.lms.dto.response.PageResponse;
import vn.edu.hcmut.lms.dto.response.ProfileResponse;
import vn.edu.hcmut.lms.entity.ClassRoom;
import vn.edu.hcmut.lms.entity.Enrollment;
import vn.edu.hcmut.lms.exception.*;
import vn.edu.hcmut.lms.mapper.ClassRoomMapper;
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
    ClassRoomMapper classMapper;

    /**
     * Student enrolls in a class. Handles re-enrolling for rejected requests.
     */
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

        validationService.validateBusySchedule(userId, classRoom);

        Optional<Enrollment> existingEnrollment = enrollmentRepository.findByClassRoomIdAndStudentProfileId(classId, userId);
        Enrollment enrollment;

        if (existingEnrollment.isPresent()) {
            enrollment = existingEnrollment.get();
            if (enrollment.getStatus() == EnrollmentStatus.APPROVED) {
                throw new AppException(ErrorCode.ALREADY_ENROLLED);
            }
            if (enrollment.getStatus() == EnrollmentStatus.PENDING) {
                throw new AppException(ErrorCode.ENROLLMENT_PENDING);
            }
            if (enrollment.getStatus() == EnrollmentStatus.REJECTED) {
                enrollment.setStatus(EnrollmentStatus.PENDING);
                enrollment.setEnrolledAt(LocalDateTime.now());
            }
        } else {
            enrollment = Enrollment.builder()
                    .classRoom(classRoom)
                    .studentProfileId(userId)
                    .enrolledAt(LocalDateTime.now())
                    .status(EnrollmentStatus.PENDING)
                    .build();
        }
        enrollment = enrollmentRepository.save(enrollment);

        // Sync to Neo4j Graph DB
        String topicId = classRoom.getTopic() != null ? classRoom.getTopic().getId() : null;
        graphSyncService.handleEnrollmentEvent(userId, classId, topicId);

        return enrollmentMapper.toResponse(enrollment, userProfile);
    }

    /**
     * Tutor approves or rejects an enrollment request.
     */
    @Transactional
    public void approveEnrollment(String enrollmentId, boolean isApproved) {
        String tutorId = getProfileIdFromToken();

        Enrollment enrollment = enrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new AppException(ErrorCode.ENROLLMENT_NOT_FOUND));

        if (!enrollment.getClassRoom().getTutor().getId().equals(tutorId)) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        enrollment.setStatus(isApproved ? EnrollmentStatus.APPROVED : EnrollmentStatus.REJECTED);
        enrollmentRepository.save(enrollment);
    }

    public PageResponse<EnrollmentResponse> getPendingRequestsOfClass(String classId, int page, int size) {
        String currentUserId = getProfileIdFromToken();

        ClassRoom classRoom = classRoomRepository.findById(classId)
                .orElseThrow(() -> new AppException(ErrorCode.CLASS_NOT_FOUND));

        if (!classRoom.getTutor().getId().equals(currentUserId))
            throw new AppException(ErrorCode.ACCESS_DENIED);

        Pageable pageable = PageRequest.of((page > 0) ? page - 1 : 0, size);

        Page<Enrollment> enrollments =
                enrollmentRepository.findByClassRoomIdAndStatus(classId, EnrollmentStatus.PENDING, pageable);

        List<EnrollmentResponse> responses = toEnrollmentResponses(enrollments.getContent());

        return PageResponse.<EnrollmentResponse>builder()
                .currentPage(page)
                .totalPages(enrollments.getTotalPages())
                .pageSize(enrollments.getSize())
                .totalElements(enrollments.getTotalElements())
                .data(responses)
                .build();
    }

    public PageResponse<EnrollmentResponse> getClassMembers(String classId, int page, int size) {
        Pageable pageable = PageRequest.of((page > 0) ? page - 1 : 0, size);

        Page<Enrollment> enrollments =
                enrollmentRepository.findByClassRoomIdAndStatus(classId, EnrollmentStatus.APPROVED, pageable);

        List<EnrollmentResponse> responses = toEnrollmentResponses(enrollments.getContent());

        return PageResponse.<EnrollmentResponse>builder()
                .currentPage(page)
                .totalPages(enrollments.getTotalPages())
                .pageSize(enrollments.getSize())
                .totalElements(enrollments.getTotalElements())
                .data(responses)
                .build();
    }

    @Transactional
    public void removeStudent(String enrollmentId) {
        String tutorId = getProfileIdFromToken();

        Enrollment enrollment = enrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new AppException(ErrorCode.ENROLLMENT_NOT_FOUND));

        if (!enrollment.getClassRoom().getTutor().getId().equals(tutorId)) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        enrollmentRepository.delete(enrollment);
    }

    // --- Helper Methods ---
    private String getProfileIdFromToken() {
        var jwt = (Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return jwt.getClaimAsString("profile_id");
    }

    /**
     * Batch retrieves student profiles.
     */
    private List<EnrollmentResponse> toEnrollmentResponses(List<Enrollment> enrollments) {
        Set<String> studentIds = enrollments.stream()
                .map(Enrollment::getStudentProfileId)
                .collect(Collectors.toSet());

        if (studentIds.isEmpty()) return new ArrayList<>();

        var profiles = profileClient.getProfiles(new ArrayList<>(studentIds)).getResult();

        Map<String, ProfileResponse> profileMap = profiles.stream()
                .collect(Collectors.toMap(ProfileResponse::getId, Function.identity()));

        return enrollmentMapper.toResponseList(enrollments, profileMap);
    }
}
