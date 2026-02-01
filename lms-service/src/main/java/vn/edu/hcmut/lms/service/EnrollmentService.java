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
import vn.edu.hcmut.lms.exception.AppException;
import vn.edu.hcmut.lms.exception.ErrorCode;
import vn.edu.hcmut.lms.mapper.EnrollmentMapper;
import vn.edu.hcmut.lms.repository.ClassRoomRepository;
import vn.edu.hcmut.lms.repository.EnrollmentRepository;
import vn.edu.hcmut.lms.repository.httpclient.ProfileClient;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class EnrollmentService {
    EnrollmentRepository enrollmentRepository;
    ClassRoomRepository classRoomRepository;
    ProfileClient profileClient;

    EnrollmentMapper enrollmentMapper;

    @Transactional
    public EnrollmentResponse enrollClass(String classId) {
        String studentId = getProfileIdFromToken();

        ClassRoom classRoom = classRoomRepository.findById(classId)
                .orElseThrow(() -> new AppException(ErrorCode.CLASS_NOT_FOUND));

        if (classRoom.getStatus() != ClassStatus.ENROLLING) throw new AppException(ErrorCode.CLASS_NOT_AVAILABLE);

        if (enrollmentRepository.existsByClassRoomIdAndStudentProfileId(classId, studentId)) {
            throw new AppException(ErrorCode.ALREADY_ENROLLED);
        }

        if (classRoom.getTutor().getId().equals(studentId)) throw new AppException(ErrorCode.CANNOT_ENROLL_OWN_CLASS);

        Enrollment enrollment = Enrollment.builder()
                .classRoom(classRoom)
                .studentProfileId(studentId)
                .enrolledAt(LocalDateTime.now())
                .status(EnrollmentStatus.PENDING)
                .build();

        enrollment = enrollmentRepository.save(enrollment);

        return enrollmentMapper.toResponse(enrollment, null);
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

    public List<EnrollmentResponse> getClassMembers(String classId) {
        String requesterId = getProfileIdFromToken();

        ClassRoom classRoom = classRoomRepository.findById(classId)
                .orElseThrow(() -> new AppException(ErrorCode.CLASS_NOT_FOUND));


        List<Enrollment> enrollments = enrollmentRepository.findByClassRoomId(classId);

        // --- KỸ THUẬT BATCH GET PROFILE ---
        // B1: Lấy list student IDs
        List<String> studentIds = enrollments.stream()
                .map(Enrollment::getStudentProfileId)
                .distinct()
                .toList();

        // B2: Gọi 1 lần sang Profile Service
        Map<String, ProfileResponse> profileMap;
        try {
            List<ProfileResponse> profiles = profileClient.getProfiles(studentIds);
            profileMap = profiles.stream()
                    .collect(Collectors.toMap(ProfileResponse::getId, Function.identity()));
        } catch (Exception e) {
            // Fallback nếu profile service lỗi: trả về map rỗng
            profileMap = Map.of();
        }

        // B3: Map dữ liệu
        final var profilesFinal = profileMap;
        return enrollments.stream()
                .map(enrollment -> enrollmentMapper.toResponse(
                        enrollment,
                        profilesFinal.get(enrollment.getStudentProfileId())
                ))
                .toList();
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
}
