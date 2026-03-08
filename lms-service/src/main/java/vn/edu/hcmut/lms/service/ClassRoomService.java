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
import vn.edu.hcmut.lms.constant.LearningFormat;
import vn.edu.hcmut.lms.dto.request.ClassRoomCreationRequest;
import vn.edu.hcmut.lms.dto.request.ClassRoomUpdateRequest;
import vn.edu.hcmut.lms.dto.response.ClassRoomResponse;
import vn.edu.hcmut.lms.dto.response.PageResponse;
import vn.edu.hcmut.lms.dto.response.TutorResponse;
import vn.edu.hcmut.lms.dto.response.TutorSearchResponse;
import vn.edu.hcmut.lms.entity.ClassRoom;
import vn.edu.hcmut.lms.entity.Topic;
import vn.edu.hcmut.lms.entity.Tutor;
import vn.edu.hcmut.lms.exception.AppException;
import vn.edu.hcmut.lms.exception.ErrorCode;
import vn.edu.hcmut.lms.mapper.ClassRoomMapper;
import vn.edu.hcmut.lms.repository.ClassRoomRepository;
import vn.edu.hcmut.lms.repository.TopicRepository;
import vn.edu.hcmut.lms.repository.TutorRepository;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ClassRoomService {
    ClassRoomRepository classRoomRepository;
    TopicRepository topicRepository;
    TutorRepository tutorRepository;
    ClassRoomMapper classMapper;
    ValidationService validationService;

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

        return classMapper.toResponse(classRoom);
    }

    /**
     * Retrieves a paginated list of classes for the currently authenticated tutor.
     */
    public PageResponse<ClassRoomResponse> getMyClassesAsTutor(int page, int size) {
        String profileId = getProfileIdFromToken();
        Pageable pageable = PageRequest.of((page > 0) ? page - 1 : 0, size);

        Page<ClassRoom> classes = classRoomRepository.findByTutorId(profileId, pageable);

        List<ClassRoomResponse> classResponses = classes.getContent().stream()
                .map(classMapper::toResponse)
                .toList();

        return PageResponse.<ClassRoomResponse>builder()
                .currentPage(page)
                .totalPages(classes.getTotalPages())
                .pageSize(classes.getSize())
                .totalElements(classes.getTotalElements())
                .data(classResponses)
                .build();
    }

    /**
     * Retrieves a paginated list of classes for a specific tutor ID.
     */
    public PageResponse<ClassRoomResponse> getClassesOfTutor(String tutorId, int page, int size) {
        Pageable pageable = PageRequest.of((page > 0) ? page - 1 : 0, size);

        Page<ClassRoom> classes = classRoomRepository.findByTutorId(tutorId, pageable);

        List<ClassRoomResponse> classResponses = classes.getContent().stream()
                .map(classMapper::toResponse)
                .toList();

        return PageResponse.<ClassRoomResponse>builder()
                .currentPage(page)
                .totalPages(classes.getTotalPages())
                .pageSize(classes.getSize())
                .totalElements(classes.getTotalElements())
                .data(classResponses)
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

        return classMapper.toResponse(classRoomRepository.save(classRoom));
    }

    /**
     * Performs a soft delete by changing the class status to CANCEL.
     */
    @Transactional
    public void deleteClass(String classId) {
        String profileId = getProfileIdFromToken();

        ClassRoom classRoom = classRoomRepository.findById(classId)
                .orElseThrow(() -> new AppException(ErrorCode.CLASS_NOT_FOUND));

        if (!classRoom.getTutor().getId().equals(profileId)) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        classRoom.setStatus(ClassStatus.CANCELLED);
        classRoomRepository.save(classRoom);
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
        var jwt = (Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return jwt.getClaimAsString("profile_id");
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
