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
import vn.edu.hcmut.lms.constant.LearningFormat;
import vn.edu.hcmut.lms.dto.request.ClassRoomCreationRequest;
import vn.edu.hcmut.lms.dto.request.ClassRoomUpdateRequest;
import vn.edu.hcmut.lms.dto.response.ClassRoomResponse;
import vn.edu.hcmut.lms.dto.response.PageResponse;
import vn.edu.hcmut.lms.dto.response.TutorResponse;
import vn.edu.hcmut.lms.dto.response.TutorSearchResponse;
import vn.edu.hcmut.lms.entity.*;
import vn.edu.hcmut.lms.exception.AppException;
import vn.edu.hcmut.lms.exception.ErrorCode;
import vn.edu.hcmut.lms.mapper.ClassRoomMapper;
import vn.edu.hcmut.lms.mapper.TutorMapper;
import vn.edu.hcmut.lms.repository.ClassRoomRepository;
import vn.edu.hcmut.lms.repository.EnrollmentRepository;
import vn.edu.hcmut.lms.repository.TopicRepository;
import vn.edu.hcmut.lms.repository.TutorRepository;
import vn.edu.hcmut.lms.utils.ClassroomUserStatusResolver;
import vn.edu.hcmut.lms.utils.SecurityUtils;
import vn.edu.hcmut.lms.utils.VietnameseTextUtils;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class ClassRoomService {
    // --- Repositories ---
    ClassRoomRepository classRoomRepository;
    TopicRepository topicRepository;
    TutorRepository tutorRepository;
    EnrollmentRepository enrollmentRepository;

    // --- Mappers ---
    ClassRoomMapper classMapper;
    TutorMapper tutorMapper;

    // --- Supporting services ---
    ValidationService validationService;
    ClassRoomSyncService classRoomSyncService;
    ClassroomUserStatusResolver statusResolver;
    SecurityUtils securityUtils;

    /**
     * Creates a new classroom with the authenticated user as the tutor.
     * Assigns topic (if provided), establishes schedule bidirectional links,
     * validates schedule conflicts, persists, then syncs to Neo4j.
     */
    @Transactional
    public ClassRoomResponse createClass(ClassRoomCreationRequest request) {
        String profileId = securityUtils.getProfileId();

        Tutor tutor = tutorRepository.findById(profileId)
                .orElseThrow(() -> new AppException(ErrorCode.TUTOR_NOT_FOUND));

        ClassRoom classRoom = classMapper.toClassRoom(request);
        classRoom.setTutor(tutor);
        classRoom.setStatus(ClassStatus.ENROLLING);

        assignTopicIfPresent(request.getTopicId(), null, classRoom);

        validationService.validateBusySchedule(profileId, classRoom);

        classRoom = classRoomRepository.save(classRoom);
        classRoomSyncService.synchronization(classRoom);

        return classMapper.toResponse(classRoom);
    }

    /**
     * Updates an existing classroom. Only the owning tutor may call this.

     * Update order:
     *   1. assertOwner           — fail fast before any mutation
     *   2. validateStatusTrans.  — validate before applying
     *   3. updateClass (mapper)  — apply scalar fields (name, description, dates)
     *   4. applyStatus           — set validated status
     *   5. assignTopic           — handle topicId / clearTopic
     *   6. replaceSchedules      — replace schedules if provided
     *   7. save + sync
     */
    @Transactional
    public ClassRoomResponse updateClass(String classId, ClassRoomUpdateRequest request) {
        String profileId = securityUtils.getProfileId();

        ClassRoom classRoom = classRoomRepository.findById(classId)
                .orElseThrow(() -> new AppException(ErrorCode.CLASS_NOT_FOUND));

        assertOwner(classRoom, profileId);

        if (request.getStatus() != null) {
            validateStatusTransition(classRoom.getStatus(), request.getStatus());
        }

        classMapper.updateClass(classRoom, request);

        if (request.getStatus() != null) {
            classRoom.setStatus(request.getStatus());
        }

        assignTopicIfPresent(request.getTopicId(), request.getClearTopic(), classRoom);

        replaceSchedulesIfPresent(request.getSchedules(), classRoom);

        classRoom = classRoomRepository.save(classRoom);
        classRoomSyncService.synchronization(classRoom);

        return classMapper.toResponse(classRoom);
    }

    /**
     * Performs a soft delete by changing the class status to CANCEL.
     */
    @Transactional
    public void deleteClass(String classId) {
        String profileId = securityUtils.getProfileId();

        ClassRoom classroom = classRoomRepository.findById(classId)
                .orElseThrow(() -> new AppException(ErrorCode.CLASS_NOT_FOUND));

        assertOwner(classroom, profileId);

        classRoomRepository.delete(classroom);
        classRoomSyncService.remove(classId);
    }

    /**
     * Returns a paginated list of classrooms owned by the authenticated tutor.
     * Every entry is tagged with userStatus = "OWNER".
     */
    @Transactional(readOnly = true)
    public PageResponse<ClassRoomResponse> getMyClassesAsTutor(int page, int size) {
        String profileId = securityUtils.getProfileId();
        Pageable pageable = toPageable(page, size);

        Page<ClassRoom> classes = classRoomRepository.findByTutorId(profileId, pageable);

        List<ClassRoomResponse> responses = classes.getContent().stream()
                .map(c -> {
                    ClassRoomResponse res = classMapper.toResponse(c);
                    res.setUserStatus("OWNER");
                    return res;
                })
                .toList();

        return buildPageResponse(responses, page, classes);
    }

    /**
     * Returns classrooms the authenticated student has enrolled in,
     * filtered by the given EnrollmentStatus.
     */
    @Transactional(readOnly = true)
    public PageResponse<ClassRoomResponse> getMyClassesByEnrollmentStatus(
            EnrollmentStatus status, int page, int size) {

        String profileId = securityUtils.getProfileId();
        Pageable pageable = toPageable(page, size);

        Page<Enrollment> enrollments =
                enrollmentRepository.findByStudentProfileIdAndStatus(profileId, status, pageable);

        List<ClassRoomResponse> responses = enrollments.getContent().stream()
                .map(e -> {
                    ClassRoomResponse res = classMapper.toResponse(e.getClassRoom());
                    res.setUserStatus(e.getStatus().name());
                    return res;
                })
                .toList();

        return buildPageResponse(responses, page, enrollments);
    }

    /**
     * Returns classrooms belonging to a specific tutor.
     * Resolves the calling user's relationship status for each classroom in batch.
     */
    public PageResponse<ClassRoomResponse> getClassesOfTutor(String tutorId, int page, int size) {
        Pageable pageable = toPageable(page, size);
        Page<ClassRoom> classes = classRoomRepository.findByTutorId(tutorId, pageable);

        String userId = securityUtils.getSafeProfileId();
        Map<String, String> statusMap = statusResolver.resolveBatch(classes.getContent(), userId);

        List<ClassRoomResponse> responses = classes.getContent().stream()
                .map(c -> {
                    ClassRoomResponse res = classMapper.toResponse(c);
                    res.setUserStatus(statusMap.get(c.getId()));
                    return res;
                })
                .toList();

        return buildPageResponse(responses, page, classes);
    }

    /**
     * Returns a single classroom by ID with the caller's relationship status resolved.
     * Accessible to anonymous users (userStatus will be "NONE").
     */
    @Transactional(readOnly = true)
    public ClassRoomResponse getClassRoomById(String classId) {
        ClassRoom classroom = classRoomRepository.findClassRoomById(classId)
                .orElseThrow(() -> new AppException(ErrorCode.CLASS_NOT_FOUND));

        String userId = securityUtils.getSafeProfileId();

        ClassRoomResponse response = classMapper.toResponse(classroom);
        response.setUserStatus(statusResolver.resolve(classroom, userId));

        return response;
    }

    /**
     * Searches available classrooms by subject, topic, format, and keyword,
     * then groups results by tutor.
     * NOTE: Results are grouped in-memory after a filtered DB query.
     * A hard cap of MAX_SEARCH_RESULTS is applied to prevent OOM on large datasets.
     * Consider pushing the GROUP BY to the query layer when traffic grows.
     */
    public PageResponse<TutorSearchResponse> searchClassesGroupedByTutor(
            String subjectName,
            String topicName,
            LearningFormat format,
            String userSearchKeyword,
            int page,
            int size) {

        String subject = VietnameseTextUtils.toLikePattern(subjectName);
        String topic   = VietnameseTextUtils.toLikePattern(topicName);
        String keyword = VietnameseTextUtils.toLikePattern(userSearchKeyword);

        List<ClassRoom> matches =
                classRoomRepository.searchAvailableClasses(subject, topic, format, keyword);

        if (matches.isEmpty()) {
            return PageResponse.<TutorSearchResponse>builder()
                    .currentPage(page).totalPages(0)
                    .pageSize(size).totalElements(0L)
                    .data(new ArrayList<>())
                    .build();
        }

        List<TutorSearchResponse> results = groupByTutor(matches);

        return paginateInMemory(results, page, size);
    }

    private void assignTopicIfPresent(String topicId, Boolean clearTopic, ClassRoom classRoom) {
        if (Boolean.TRUE.equals(clearTopic)) {
            classRoom.setTopic(null);
            return;
        }
        if (topicId == null) return;

        Topic topic = topicRepository.findById(topicId)
                .orElseThrow(() -> new AppException(ErrorCode.TOPIC_NOT_FOUND));
        classRoom.setTopic(topic);
    }

    private void replaceSchedulesIfPresent(
            List<ClassRoomUpdateRequest.ScheduleRequest> scheduleRequests,
            ClassRoom classRoom) {

        if (scheduleRequests == null) return;

        List<ClassSchedule> newSchedules = scheduleRequests.stream()
                .map(classMapper::toScheduleEntity)
                .peek(s -> s.setClassRoom(classRoom))
                .collect(Collectors.toCollection(ArrayList::new));

        classRoom.getSchedules().clear();
        classRoom.getSchedules().addAll(newSchedules);
    }

    private void validateStatusTransition(ClassStatus current, ClassStatus next) {
        boolean valid = switch (current) {
            case ENROLLING -> next == ClassStatus.ONGOING   || next == ClassStatus.CANCELLED;
            case ONGOING   -> next == ClassStatus.FINISHED || next == ClassStatus.CANCELLED;
            default        -> false;
        };

        if (!valid) {
            throw new AppException(ErrorCode.INVALID_STATUS_TRANSITION,
                    String.format("Cannot transition from %s to %s", current, next));
        }
    }

    private void assertOwner(ClassRoom classRoom, String profileId) {
        if (!classRoom.getTutor().getId().equals(profileId)) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }
    }

    private List<TutorSearchResponse> groupByTutor(List<ClassRoom> classRooms) {
        Map<String, List<ClassRoom>> grouped = classRooms.stream()
                .collect(Collectors.groupingBy(c -> c.getTutor().getId()));

        return grouped.values().stream()
                .map(classes -> {
                    Tutor tutor = classes.get(0).getTutor();
                    TutorResponse tutorResponse = tutorMapper.toResponse(tutor);

                    List<ClassRoomResponse> classResponses = classes.stream()
                            .map(classMapper::toResponse)
                            .toList();

                    return TutorSearchResponse.builder()
                            .tutor(tutorResponse)
                            .matchingClasses(classResponses)
                            .build();
                })
                .toList();
    }

    private <T> PageResponse<T> paginateInMemory(List<T> data, int page, int size) {
        int totalElements = data.size();
        int totalPages    = (int) Math.ceil((double) totalElements / size);
        int from          = Math.max(0, (page > 0 ? page - 1 : 0) * size);
        int to            = Math.min(from + size, totalElements);

        List<T> paged = (from < totalElements) ? data.subList(from, to) : new ArrayList<>();

        return PageResponse.<T>builder()
                .currentPage(page)
                .totalPages(totalPages)
                .pageSize(size)
                .totalElements(totalElements)
                .data(paged)
                .build();
    }

    private Pageable toPageable(int page, int size) {
        return PageRequest.of((page > 0) ? page - 1 : 0, size);
    }

    private <T, E> PageResponse<T> buildPageResponse(List<T> data, int page, Page<E> source) {
        return PageResponse.<T>builder()
                .currentPage(page)
                .totalPages(source.getTotalPages())
                .pageSize(source.getSize())
                .totalElements(source.getTotalElements())
                .data(data)
                .build();
    }
}
