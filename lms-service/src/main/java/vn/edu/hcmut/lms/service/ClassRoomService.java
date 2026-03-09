package vn.edu.hcmut.lms.service;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmut.lms.constant.ClassStatus;
import vn.edu.hcmut.lms.constant.EnrollmentStatus;
import vn.edu.hcmut.lms.constant.LearningFormat;
import vn.edu.hcmut.lms.dto.request.ClassRoomCreationRequest;
import vn.edu.hcmut.lms.dto.request.ClassRoomUpdateRequest;
import vn.edu.hcmut.lms.dto.response.ClassRoomResponse;
import vn.edu.hcmut.lms.dto.response.PageResponse;
import vn.edu.hcmut.lms.dto.response.TutorResponse;
import vn.edu.hcmut.lms.dto.response.TutorSearchResponse;
import vn.edu.hcmut.lms.dto.sync.ClassRoomSyncRequest;
import vn.edu.hcmut.lms.entity.ClassRoom;
import vn.edu.hcmut.lms.entity.Enrollment;
import vn.edu.hcmut.lms.entity.Topic;
import vn.edu.hcmut.lms.entity.Tutor;
import vn.edu.hcmut.lms.exception.AppException;
import vn.edu.hcmut.lms.exception.ErrorCode;
import vn.edu.hcmut.lms.mapper.ClassRoomMapper;
import vn.edu.hcmut.lms.repository.ClassRoomRepository;
import vn.edu.hcmut.lms.repository.EnrollmentRepository;
import vn.edu.hcmut.lms.repository.TopicRepository;
import vn.edu.hcmut.lms.repository.TutorRepository;
import vn.edu.hcmut.lms.repository.httpclient.ProfileClient;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class ClassRoomService {
    ClassRoomRepository classRoomRepository;
    TopicRepository topicRepository;
    TutorRepository tutorRepository;
    EnrollmentRepository enrollmentRepository;
    ClassRoomMapper classMapper;
    ValidationService validationService;

    ProfileClient profileClient;

    // Vietnamese string processing
    private static final Pattern DIACRITICAL_MARKS_PATTERN = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");

    /**
     * Creates a new classroom with the authenticated user as the tutor.
     * Links topic, schedules, and validates if the tutor's schedule is free.
     */
    @Transactional
    public ClassRoomResponse createClass(ClassRoomCreationRequest request) {
        String profileId = getProfileIdFromToken();

        Tutor tutor = tutorRepository.findById(profileId)
                .orElseThrow(() -> new AppException(ErrorCode.TUTOR_NOT_FOUND));

        ClassRoom classRoom = classMapper.toClassRoom(request);
        classRoom.setTutor(tutor);
        classRoom.setStatus(ClassStatus.ENROLLING);

        // assign TOPIC if provided
        if (request.getTopicId() != null) {
            Topic topic = topicRepository.findById(request.getTopicId())
                    .orElseThrow(() -> new AppException(ErrorCode.TOPIC_NOT_FOUND));
            classRoom.setTopic(topic);
        }

        // Establish bidirectional relationship for schedules
        if (classRoom.getSchedules() != null) {
            ClassRoom c = classRoom;
            classRoom.getSchedules().forEach(schedule -> schedule.setClassRoom(c));
        }

        // Validate tutor's schedule constraints
        validationService.validateBusySchedule(profileId, classRoom);

        classRoom = classRoomRepository.save(classRoom);

        // Neo4j sync
        try {
            ClassRoomSyncRequest classroom = ClassRoomSyncRequest.builder()
                    .id(classRoom.getId())
                    .name(classRoom.getName())
                    .status(classRoom.getStatus() != null ? classRoom.getStatus().name() : null)
                    .format(classRoom.getFormat() != null ? classRoom.getFormat().name() : null)
                    .topicId(classRoom.getTopic() != null ? classRoom.getTopic().getId() : null)
                    .build();

            profileClient.syncClassRoom(classroom);
        } catch (Exception e) {
            log.error("Failed to sync classroom {} to Neo4j. Will need manual sync later.", classRoom.getId(), e);
            throw new AppException(ErrorCode.SYNC_FAILED);
        }

        return classMapper.toResponse(classRoom);
    }

    /**
     * Retrieves a paginated list of classes for the currently authenticated tutor.
     */
    public PageResponse<ClassRoomResponse> getMyClassesAsTutor(int page, int size) {
        String profileId = getProfileIdFromToken();
        Pageable pageable = PageRequest.of((page > 0) ? page - 1 : 0, size);

        Page<ClassRoom> classes = classRoomRepository.findByTutorId(profileId, pageable);

        List<ClassRoomResponse> responses = classes.getContent().stream().map(c -> {
            ClassRoomResponse res = classMapper.toResponse(c);
            res.setUserStatus("OWNER");
            return res;
        }).toList();

        return PageResponse.<ClassRoomResponse>builder()
                .currentPage(page)
                .totalPages(classes.getTotalPages())
                .pageSize(classes.getSize())
                .totalElements(classes.getTotalElements())
                .data(responses)
                .build();
    }

    /**
     * Retrieves a paginated list of classes the student has enrolled in.
     */
    public PageResponse<ClassRoomResponse> getMyClassesByEnrollmentStatus(EnrollmentStatus status, int page, int size) {
        String profileId = getProfileIdFromToken();
        Pageable pageable = PageRequest.of((page > 0) ? page - 1 : 0, size);

        Page<Enrollment> enrollments = enrollmentRepository.findByStudentProfileIdAndStatus(profileId, status, pageable);

        List<ClassRoomResponse> responses = enrollments.getContent().stream().map(e -> {
            ClassRoomResponse res = classMapper.toResponse(e.getClassRoom());
            res.setUserStatus(e.getStatus().name());
            return res;
        }).toList();

        return PageResponse.<ClassRoomResponse>builder()
                .currentPage(page)
                .totalPages(enrollments.getTotalPages())
                .pageSize(enrollments.getSize())
                .totalElements(enrollments.getTotalElements())
                .data(responses)
                .build();
    }

    /**
     * Retrieves a paginated list of classes for a specific tutor ID.
     */
    public PageResponse<ClassRoomResponse> getClassesOfTutor(String tutorId, int page, int size) {
        Pageable pageable = PageRequest.of((page > 0) ? page - 1 : 0, size);
        Page<ClassRoom> classes = classRoomRepository.findByTutorId(tutorId, pageable);

        String userId = getSafeProfileIdFromToken();
        List<String> classIds = classes.getContent().stream().map(ClassRoom::getId).toList();

        Map<String, String> status = new HashMap<>();
        if (userId != null && !classIds.isEmpty()) {
            List<Enrollment> enrollments = enrollmentRepository.findByStudentProfileIdAndClassRoomIdIn(userId, classIds);
            for (Enrollment e : enrollments) {
                status.put(e.getClassRoom().getId(), e.getStatus().name());
            }
        }

        List<ClassRoomResponse> responses = classes.getContent().stream().map(c -> {
            ClassRoomResponse res = classMapper.toResponse(c);

            if (userId == null) {
                res.setUserStatus("NONE");
            } else if (c.getTutor().getId().equals(userId)) {
                res.setUserStatus("OWNER");
            } else {
                res.setUserStatus(status.getOrDefault(c.getId(), "NONE"));
            }
            return res;
        }).toList();

        return PageResponse.<ClassRoomResponse>builder()
                .currentPage(page)
                .totalPages(classes.getTotalPages())
                .pageSize(classes.getSize())
                .totalElements(classes.getTotalElements())
                .data(responses)
                .build();
    }

    /**
     * Updates details of an existing class.
     * Ensures only the tutor who owns the class can modify it.
     */
    @Transactional
    public ClassRoomResponse updateClass(String classId, ClassRoomUpdateRequest request) {
        String profileId = getProfileIdFromToken();

        ClassRoom classRoom = classRoomRepository.findById(classId)
                .orElseThrow(() -> new AppException(ErrorCode.CLASS_NOT_FOUND));

        if (!classRoom.getTutor().getId().equals(profileId)) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        classMapper.updateClass(classRoom, request);

        // Update topic if requested
        if (request.getTopicId() != null) {
            Topic topic = topicRepository.findById(request.getTopicId())
                    .orElseThrow(() -> new AppException(ErrorCode.TOPIC_NOT_FOUND));
            classRoom.setTopic(topic);
        }

        classRoomRepository.save(classRoom);

        try {
            ClassRoomSyncRequest classroom = ClassRoomSyncRequest.builder()
                    .id(classRoom.getId())
                    .name(classRoom.getName())
                    .status(classRoom.getStatus() != null ? classRoom.getStatus().name() : null)
                    .format(classRoom.getFormat() != null ? classRoom.getFormat().name() : null)
                    .topicId(classRoom.getTopic() != null ? classRoom.getTopic().getId() : null)
                    .build();

            profileClient.syncClassRoom(classroom);
        } catch (Exception e) {
            log.error("Failed to sync updated classroom {} to Neo4j.", classRoom.getId(), e);
            throw new AppException(ErrorCode.SYNC_FAILED);
        }

        return classMapper.toResponse(classRoom);
    }

    /**
     * Performs a soft delete by changing the class status to CANCEL.
     */
    @Transactional
    public void deleteClass(String classId) {
        String profileId = getProfileIdFromToken();

        ClassRoom classroom = classRoomRepository.findById(classId)
                .orElseThrow(() -> new AppException(ErrorCode.CLASS_NOT_FOUND));

        if (!classroom.getTutor().getId().equals(profileId)) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        classRoomRepository.delete(classroom);

        try {
            profileClient.deleteClassRoom(classId);
        } catch (Exception e) {
            log.error("Failed to delete ClassRoom {} from Neo4j.", classId, e);
            throw new AppException(ErrorCode.SYNC_FAILED);
            // TODO: Kafka/Message Queue here if require integrity.
        }
    }

    @Transactional
    public void leaveClass(String classId) {
        String userId = getProfileIdFromToken();

        Enrollment enrollment = enrollmentRepository.findByClassRoomIdAndStudentProfileId(classId, userId)
                .orElseThrow(() -> new AppException(ErrorCode.ENROLLMENT_NOT_FOUND));

        enrollmentRepository.delete(enrollment);

        try {
            profileClient.removeEnrollment(userId, classId);
        } catch (Exception e) {
            log.error("Failed to remove enrollment from Neo4j when student left. " +
                    "Student: {}, Class: {}", userId, classId, e);
        }
    }

    @Transactional
    public void removeStudent(String classId, String studentId) {
        String tutorId = getProfileIdFromToken();

        Enrollment enrollment = enrollmentRepository.findByClassRoomIdAndStudentProfileId(classId, studentId)
                .orElseThrow(() -> new AppException(ErrorCode.ENROLLMENT_NOT_FOUND));

        if (!enrollment.getClassRoom().getTutor().getId().equals(tutorId)) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        enrollmentRepository.delete(enrollment);

        try {
            profileClient.removeEnrollment(studentId, classId);
        } catch (Exception e) {
            log.error("Failed to remove enrollment from Neo4j. Student: {}, Class: {}", studentId, classId, e);
            throw new AppException(ErrorCode.SYNC_FAILED);
        }
    }

    public ClassRoomResponse getClassRoomById(String classId) {
        ClassRoom classRoom = classRoomRepository.findClassRoomById(classId)
                .orElseThrow(() -> new AppException(ErrorCode.CLASS_NOT_FOUND));

        return classMapper.toResponse(classRoom);
    }

    /**
     * Searches for available classes based on various filters and groups the results by Tutor.
     */
    public PageResponse<TutorSearchResponse> searchClassesGroupedByTutor(
            String subjectName,
            String topicName,
            LearningFormat format,
            String userSearchKeyword,
            int page,
            int size) {

        String subject = processKeyword(subjectName);
        String topic = processKeyword(topicName);
        String keyword = processKeyword(userSearchKeyword);

        List<ClassRoom> matches = classRoomRepository.searchAvailableClasses(subject, topic, format, keyword);

        if (matches.isEmpty()) {
            return PageResponse.<TutorSearchResponse>builder()
                    .currentPage(page)
                    .totalPages(0)
                    .pageSize(size)
                    .totalElements(0L)
                    .data(new ArrayList<>()).build();
        }

        // Group by tutor ID
        Map<String, List<ClassRoom>> classesGrouped = matches.stream()
                .collect(Collectors.groupingBy(classRoom -> classRoom.getTutor().getId()));

        List<TutorSearchResponse> results = new ArrayList<>();

        for (var classes : classesGrouped.values()) {
            // Extract tutor from the first class in the list (all classes in this list share the same tutor).
            var tutor = classes.get(0).getTutor();

            TutorResponse tutorResponse = TutorResponse.builder()
                    .id(tutor.getId())
                    .introduction(tutor.getIntroduction())
                    .averageRating(tutor.getAverageRating())
                    .ratingCount(tutor.getRatingCount())
                    .status(tutor.getStatus())
                    .name(tutor.getName())
                    .avatar(tutor.getAvatar())
                    .build();

            List<ClassRoomResponse> classResponses = classes.stream()
                    .map(classMapper::toResponse)
                    .toList();

            results.add(TutorSearchResponse.builder()
                    .tutor(tutorResponse)
                    .matchingClasses(classResponses)
                    .build());
        }

        // In-Memory Pagination
        int totalElements = results.size();
        int totalPages = (int) Math.ceil((double) totalElements / size);

        // Calculate index to slice the array
        int from = (page > 0 ? page - 1 : 0) * size;
        int to = Math.min(from + size, totalElements);

        List<TutorSearchResponse> pagedResults = new ArrayList<>();

        if (from < totalElements) pagedResults = results.subList(from, to);

        return PageResponse.<TutorSearchResponse>builder()
                .currentPage(page)
                .totalPages(totalPages)
                .pageSize(size)
                .totalElements(totalElements)
                .data(pagedResults)
                .build();
    }

    private String getProfileIdFromToken() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null ||
            !authentication.isAuthenticated() ||
            authentication.getPrincipal().equals("anonymousUser")) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        if (authentication.getPrincipal() instanceof Jwt jwt) {
            String profileId = jwt.getClaimAsString("profile_id");

            if (profileId == null) {
                throw new AppException(ErrorCode.INVALID_TOKEN_CLAIMS);
            }
            return profileId;
        }

        throw new AppException(ErrorCode.UNAUTHENTICATED);
    }

    private String getSafeProfileIdFromToken() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null ||
            !authentication.isAuthenticated() ||
            authentication.getPrincipal().equals("anonymousUser")) {
            return null;
        }
        if (authentication.getPrincipal() instanceof Jwt jwt) {
            return jwt.getClaimAsString("profile_id");
        }
        return null;
    }

    private String processKeyword(String keyword) {
        String standardizedKeywords = standardization(keyword);
        if (standardizedKeywords == null) {
            return null;
        }
        return "%" + standardizedKeywords + "%";
    }

    private String standardization(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) return null;

        String normalized = Normalizer.normalize(keyword.trim(), Normalizer.Form.NFD);

        return DIACRITICAL_MARKS_PATTERN.matcher(normalized).replaceAll("")
                .replace("Đ", "D")
                .replace("đ", "d")
                .toLowerCase();
    }
}
