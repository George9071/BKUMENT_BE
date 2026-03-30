package vn.edu.hcmut.lms.service;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmut.lms.constant.ClassStatus;
import vn.edu.hcmut.lms.constant.EnrollmentStatus;
import vn.edu.hcmut.lms.dto.response.EnrollmentResponse;
import vn.edu.hcmut.lms.dto.response.PageResponse;
import vn.edu.hcmut.lms.dto.response.ProfileResponse;
import vn.edu.hcmut.lms.entity.ClassRoom;
import vn.edu.hcmut.lms.entity.Enrollment;
import vn.edu.hcmut.lms.exception.*;
import vn.edu.hcmut.lms.mapper.EnrollmentMapper;
import vn.edu.hcmut.lms.repository.ClassRoomRepository;
import vn.edu.hcmut.lms.repository.EnrollmentRepository;
import vn.edu.hcmut.lms.repository.httpclient.ProfileClient;
import vn.edu.hcmut.lms.utils.SecurityUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class EnrollmentService {

    private static final int ENROLLMENT_COOLDOWN_DAYS = 1;
    private static final int CANCELLED_COOLDOWN_DAYS  = 3;

    // --- Repositories and clients ---
    EnrollmentRepository enrollmentRepository;
    ClassRoomRepository classRoomRepository;
    ProfileClient profileClient;

    // --- Supporting services ---
    ValidationService validationService;
    GraphSyncService graphSyncService;
    EnrollmentNotificationService notificationService;
    EnrollmentMapper enrollmentMapper;
    SecurityUtils securityUtils;


    /**
     * Enrolls the authenticated student in a class.
     * Handles re-enrollment for previously rejected requests after the cooldown period.
     * Flow: validate → upsert enrollment → sync Neo4j → send Kafka notification
     */
    @Transactional
    public EnrollmentResponse enrollClass(String classId) {
        String userId = securityUtils.getProfileId();

        ClassRoom classRoom = classRoomRepository.findById(classId)
                .orElseThrow(() -> new AppException(ErrorCode.CLASS_NOT_FOUND));

        validateEnrollmentEligibility(classRoom, userId);

        Enrollment enrollment = getOrCreateEnrollment(classId, userId, classRoom);
        enrollment = enrollmentRepository.save(enrollment);

        String topicId = classRoom.getTopic() != null ? classRoom.getTopic().getId() : null;
        graphSyncService.handleEnrollmentEvent(userId, classId, topicId);

        var profile = profileClient.getProfile(userId).getResult();

        notificationService.sendEnrollmentRequested(
                classRoom.getId(),
                classRoom.getName(),
                userId,
                profile.getLastName() + " " + profile.getFirstName(),
                classRoom.getTutor().getId()
        );

        return enrollmentMapper.toResponse(enrollment, profile);
    }

    /**
     * Allows the authenticated student to leave a class they are enrolled in.
     * Sync failure is logged but NOT re-thrown — leaving is a student-initiated
     * action and should not be blocked by a Neo4j outage. The inconsistency
     * can be resolved by a scheduled reconciliation job.
     * TODO: Replace fire-and-forget with Outbox Pattern for guaranteed delivery.
     */
    @Transactional
    public void leaveClass(String classId) {
        String userId = securityUtils.getProfileId();

        Enrollment enrollment = enrollmentRepository
                .findByClassRoomIdAndStudentProfileId(classId, userId)
                .orElseThrow(() -> new AppException(ErrorCode.ENROLLMENT_NOT_FOUND));

        enrollmentRepository.delete(enrollment);
        log.info("Student {} left class {}", userId, classId);

        try {
            graphSyncService.removeEnrollment(userId, classId);
        } catch (Exception e) {
            log.error("Failed to remove ENROLLED_IN relation from Neo4j " +
                    "(best-effort). Student: {}, Class: {}", userId, classId, e);
        }
    }

    /**
     * Allows the owning tutor to forcibly remove a student from their class.
     * Unlike leaveClass(), sync failure here IS re-thrown because this is a
     * tutor-initiated administrative action — data consistency is required.
     */
    @Transactional
    public void removeStudent(String classId, String studentId) {
        String tutorId = securityUtils.getProfileId();

        Enrollment enrollment = enrollmentRepository
                .findByClassRoomIdAndStudentProfileId(classId, studentId)
                .orElseThrow(() -> new AppException(ErrorCode.ENROLLMENT_NOT_FOUND));

        assertClassOwner(enrollment.getClassRoom(), tutorId);

        enrollmentRepository.delete(enrollment);
        log.info("Tutor {} removed student {} from class {}", tutorId, studentId, classId);

        try {
            graphSyncService.removeEnrollment(studentId, classId);
        } catch (Exception e) {
            log.error("Failed to remove ENROLLED_IN relation from Neo4j. " +
                    "Student: {}, Class: {}", studentId, classId, e);
            throw new AppException(ErrorCode.SYNC_FAILED);
        }
    }

    /**
     * Approves or rejects a pending enrollment request.
     * On approval, syncs the new ENROLLED_IN relationship to Neo4j.
     * Sends a Kafka notification to the student regardless of decision.
     */
    @Transactional
    public void approveEnrollment(String enrollmentId, boolean isApproved) {
        String tutorId = securityUtils.getProfileId();

        Enrollment enrollment = enrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new AppException(ErrorCode.ENROLLMENT_NOT_FOUND));

        ClassRoom classroom = enrollment.getClassRoom();
        assertClassOwner(classroom, tutorId);

        enrollment.setStatus(isApproved ? EnrollmentStatus.APPROVED : EnrollmentStatus.REJECTED);
        enrollmentRepository.save(enrollment);

        if (isApproved) {
            syncApprovedEnrollment(enrollment.getStudentProfileId(), classroom.getId());
        }

        notificationService.sendEnrollmentDecision(
                classroom.getId(),
                classroom.getName(),
                enrollment.getStudentProfileId(),
                tutorId,
                isApproved
        );
    }

    /**
     * Returns paginated pending enrollment requests for a class.
     * Only accessible by the owning tutor.
     */
    @Transactional(readOnly = true)
    public PageResponse<EnrollmentResponse> getPendingRequestsOfClass(String classId, int page, int size) {
        String tutorId = securityUtils.getProfileId();

        ClassRoom classRoom = classRoomRepository.findById(classId)
                .orElseThrow(() -> new AppException(ErrorCode.CLASS_NOT_FOUND));

        assertClassOwner(classRoom, tutorId);

        Pageable pageable = toPageable(page, size);
        Page<Enrollment> enrollments = enrollmentRepository
                .findByClassRoomIdAndStatus(classId, EnrollmentStatus.PENDING, pageable);

        return buildPageResponse(enrollments, page);
    }

    /**
     * Returns paginated approved members of a class.
     * Accessible to any authenticated user (e.g., to show class roster).
     */
    @Transactional(readOnly = true)
    public PageResponse<EnrollmentResponse> getClassMembers(String classId, int page, int size) {
        Pageable pageable = toPageable(page, size);
        Page<Enrollment> enrollments = enrollmentRepository
                .findByClassRoomIdAndStatus(classId, EnrollmentStatus.APPROVED, pageable);

        return buildPageResponse(enrollments, page);
    }


    private void validateEnrollmentEligibility(ClassRoom classRoom, String userId) {
        if (classRoom.getStatus() != ClassStatus.ENROLLING) {
            throw new AppException(ErrorCode.CLASS_NOT_AVAILABLE);
        }
        if (classRoom.getTutor() != null && classRoom.getTutor().getId().equals(userId)) {
            throw new AppException(ErrorCode.CANNOT_ENROLL_OWN_CLASS);
        }
        validationService.validateBusySchedule(userId, classRoom);
    }

    /**
     * Syncs the newly approved enrollment to the Neo4j recommendation graph.
     * Throws SYNC_FAILED on failure — approval requires data consistency.
     */
    private void syncApprovedEnrollment(String studentId, String classId) {
        try {
            graphSyncService.addEnrollment(studentId, classId);
        } catch (Exception e) {
            log.error("Failed to sync approved enrollment to Neo4j. " +
                    "Student: {}, Class: {}", studentId, classId, e);
            throw new AppException(ErrorCode.SYNC_FAILED);
        }
    }

    /**
     * Returns the existing enrollment (re-validating cooldown if REJECTED),
     * or builds a new PENDING enrollment if none exists.
     */
    private Enrollment getOrCreateEnrollment(String classId, String userId, ClassRoom classRoom) {
        return enrollmentRepository
                .findByClassRoomIdAndStudentProfileId(classId, userId)
                .map(existing -> switch (existing.getStatus()) {
                    case APPROVED -> throw new AppException(ErrorCode.ALREADY_ENROLLED);
                    case PENDING  -> throw new AppException(ErrorCode.ENROLLMENT_PENDING);
                    case COMPLETED -> throw new AppException(ErrorCode.ALREADY_COMPLETED);
                    case REJECTED  -> {
                        if (existing.getEnrolledAt()
                                .plusDays(ENROLLMENT_COOLDOWN_DAYS)
                                .isAfter(LocalDateTime.now())) {
                            throw new AppException(ErrorCode.ENROLLMENT_COOLDOWN);
                        }
                        yield resetToPending(existing);
                    }
                    case CANCELLED -> {
                        if (existing.getEnrolledAt()
                                .plusDays(CANCELLED_COOLDOWN_DAYS)
                                .isAfter(LocalDateTime.now())) {
                            throw new AppException(ErrorCode.ENROLLMENT_COOLDOWN);
                        }
                        yield resetToPending(existing);
                    }
                })
                .orElseGet(() -> Enrollment.builder()
                        .classRoom(classRoom)
                        .studentProfileId(userId)
                        .enrolledAt(LocalDateTime.now())
                        .status(EnrollmentStatus.PENDING)
                        .build());
    }

    private void assertClassOwner(ClassRoom classRoom, String tutorId) {
        if (!classRoom.getTutor().getId().equals(tutorId)) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }
    }

    private Enrollment resetToPending(Enrollment enrollment) {
        enrollment.setStatus(EnrollmentStatus.PENDING);
        enrollment.setEnrolledAt(LocalDateTime.now());
        return enrollment;
    }

    private PageResponse<EnrollmentResponse> buildPageResponse(
            Page<Enrollment> enrollments, int page) {

        List<EnrollmentResponse> responses = toEnrollmentResponses(enrollments.getContent());

        return PageResponse.<EnrollmentResponse>builder()
                .currentPage(page)
                .totalPages(enrollments.getTotalPages())
                .pageSize(enrollments.getSize())
                .totalElements(enrollments.getTotalElements())
                .data(responses)
                .build();
    }

    private List<EnrollmentResponse> toEnrollmentResponses(List<Enrollment> enrollments) {
        if (enrollments.isEmpty()) return new ArrayList<>();

        Set<String> studentIds = enrollments.stream()
                .map(Enrollment::getStudentProfileId)
                .collect(Collectors.toSet());

        Map<String, ProfileResponse> profileMap = profileClient
                .getProfiles(new ArrayList<>(studentIds))
                .getResult()
                .stream()
                .collect(Collectors.toMap(ProfileResponse::getId, Function.identity()));

        return enrollmentMapper.toResponseList(enrollments, profileMap);
    }

    private Pageable toPageable(int page, int size) {
        return PageRequest.of((page > 0) ? page - 1 : 0, size);
    }
}
