package vn.edu.hcmut.lms.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
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
import vn.edu.hcmut.lms.dto.request.internal.InternalClassRatingRequest;
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
@Slf4j
public class ClassRoomService {
    private static final int MAX_SEARCH_RESULTS = 500;

    // --- Repositories ---
    private final ClassRoomRepository classRoomRepository;
    private final TopicRepository topicRepository;
    private final TutorRepository tutorRepository;
    private final EnrollmentRepository enrollmentRepository;

    // --- Mappers ---
    private final ClassRoomMapper classMapper;
    private final TutorMapper tutorMapper;

    // --- Supporting services ---
    private final ValidationService validationService;
    private final ClassRoomSyncService classRoomSyncService;
    private final GraphSyncService graphSyncService;
    private final ClassroomUserStatusResolver statusResolver;
    private final SecurityUtils securityUtils;

    @Transactional
    public void updateClassRating(String classId, InternalClassRatingRequest request) {
        ClassRoom classRoom = classRoomRepository.findById(classId)
                .orElseThrow(() -> new AppException(ErrorCode.CLASS_NOT_FOUND));

        classRoom.setAverageRating(request.getAverageRating() != null ? request.getAverageRating() : 0.0);
        classRoom.setRatingCount(request.getRatingCount() != null ? Math.max(request.getRatingCount(), 0) : 0);

        classRoomRepository.save(classRoom);
        log.info("Updated class rating stats for id: {}, avg: {}, count: {}",
                classId, classRoom.getAverageRating(), classRoom.getRatingCount());
    }

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
        classRoom.setDescription(sanitizeHtml(request.getDescription()));

        assignTopicIfPresent(request.getTopicId(), null, classRoom);

        validationService.validateClassTiming(classRoom);
        validateBusyScheduleIfActive(profileId, classRoom);

        classRoom = classRoomRepository.save(classRoom);
        classRoomSyncService.syncClassRoom(classRoom);

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
        
        if (request.getDescription() != null) {
            classRoom.setDescription(sanitizeHtml(request.getDescription()));
        }

        if (request.getStatus() != null) {
            classRoom.setStatus(request.getStatus());
        }

        assignTopicIfPresent(request.getTopicId(), request.getClearTopic(), classRoom);

        replaceSchedulesIfPresent(request.getSchedules(), classRoom);

        validationService.validateClassTiming(classRoom);
        validateBusyScheduleIfActive(profileId, classRoom);

        classRoom = classRoomRepository.save(classRoom);
        classRoomSyncService.syncClassRoom(classRoom);

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

        validateStatusTransition(classroom.getStatus(), ClassStatus.CANCELLED);
        classroom.setStatus(ClassStatus.CANCELLED);

        classRoomRepository.save(classroom);
        classRoomSyncService.syncClassRoom(classroom);
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
                .collect(Collectors.toList());

        populateEnrollmentCounts(responses);

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
                .collect(Collectors.toList());

        populateEnrollmentCounts(responses);

        return buildPageResponse(responses, page, enrollments);
    }

    /**
     * Returns classrooms belonging to a specific tutor.
     * Resolves the calling user's relationship status for each classroom in batch.
     */
    @Transactional(readOnly = true)
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
                .collect(Collectors.toList());

        populateEnrollmentCounts(responses);

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
        
        populateEnrollmentCounts(Collections.singletonList(response));

        return response;
    }

    public Map<String, String> getClassNamesBatch(List<String> ids) {
        if (ids == null || ids.isEmpty()) return Map.of();
        return classRoomRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(ClassRoom::getId, ClassRoom::getName));
    }

    /**
     * Searches available classrooms by subject, topic, format, and keyword,
     * then groups results by tutor.
     * NOTE: Results are grouped in-memory after a filtered DB query.
     * A hard cap of MAX_SEARCH_RESULTS is applied to prevent OOM on large datasets.
     * Consider pushing the GROUP BY to the query layer when traffic grows.
     */
    @Transactional(readOnly = true)
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
                classRoomRepository.searchAvailableClasses(
                        subject, topic, format, keyword, PageRequest.of(0, MAX_SEARCH_RESULTS));

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

    private void validateBusyScheduleIfActive(String profileId, ClassRoom classRoom) {
        if (classRoom.getStatus() == ClassStatus.ENROLLING || classRoom.getStatus() == ClassStatus.ONGOING) {
            validationService.validateBusySchedule(profileId, classRoom);
        }
    }

    private void assertOwner(ClassRoom classRoom, String profileId) {
        assert classRoom.getTutor().getId() != null;
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
                            .collect(Collectors.toList());

                    populateEnrollmentCounts(classResponses);

                    return TutorSearchResponse.builder()
                            .tutor(tutorResponse)
                            .matchingClasses(classResponses)
                            .build();
                })
                .collect(Collectors.toList());
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

    @Transactional(readOnly = true)
    public PageResponse<ClassRoomResponse> getTopTrendingClasses(int page, int size) {
        Pageable pageable = toPageable(page, size);
        Page<ClassRoom> classes = classRoomRepository.findTopTrendingClasses(pageable);

        String userId = securityUtils.getSafeProfileId();
        Map<String, String> statusMap = statusResolver.resolveBatch(classes.getContent(), userId);

        List<ClassRoomResponse> responses = classes.getContent().stream()
                .map(c -> {
                    ClassRoomResponse res = classMapper.toResponse(c);
                    res.setUserStatus(statusMap.get(c.getId()));
                    return res;
                })
                .collect(Collectors.toList());

        populateEnrollmentCounts(responses);

        return buildPageResponse(responses, page, classes);
    }

    @Transactional(readOnly = true)
    public PageResponse<ClassRoomResponse> getRecommendedClasses(int page, int size) {
        String profileId = securityUtils.getProfileId();

        // 1. Fetch top 100 trending classes
        Pageable top100Pageable = PageRequest.of(0, 100);
        Page<ClassRoom> trendingPage = classRoomRepository.findTopTrendingClasses(top100Pageable);
        List<String> trendingIds = trendingPage.getContent().stream()
                .map(ClassRoom::getId)
                .toList();

        // 2. Fetch top 100 semantic/graph recommendations
        List<String> recommendedIds = graphSyncService.getRecommendedClassRoomIds(profileId, 100);

        // 3. Compute RRF Scores
        Map<String, Double> rrfScores = new HashMap<>();
        final int K = 60;

        for (int i = 0; i < trendingIds.size(); i++) {
            String id = trendingIds.get(i);
            rrfScores.put(id, rrfScores.getOrDefault(id, 0.0) + 1.0 / (K + i + 1));
        }

        for (int j = 0; j < recommendedIds.size(); j++) {
            String id = recommendedIds.get(j);
            rrfScores.put(id, rrfScores.getOrDefault(id, 0.0) + 1.0 / (K + j + 1));
        }

        // 4. Sort unique IDs by RRF Score descending
        List<String> hybridIds = new ArrayList<>(rrfScores.keySet());
        hybridIds.sort((id1, id2) -> Double.compare(rrfScores.get(id2), rrfScores.get(id1)));

        // Early return if empty
        if (hybridIds.isEmpty()) {
            return PageResponse.<ClassRoomResponse>builder()
                    .currentPage(page)
                    .totalPages(0)
                    .pageSize(size)
                    .totalElements(0L)
                    .data(new ArrayList<>())
                    .build();
        }

        // 5. In-memory pagination
        int totalElements = hybridIds.size();
        int from = Math.max(0, (page > 0 ? page - 1 : 0) * size);
        int to = Math.min(from + size, totalElements);
        List<String> pagedIds = (from < totalElements) ? hybridIds.subList(from, to) : new ArrayList<>();

        // 6. Fetch entities and sort them to match pagedIds
        List<ClassRoom> classes = classRoomRepository.findAllById(pagedIds);
        Map<String, ClassRoom> classMap = classes.stream()
                .collect(Collectors.toMap(ClassRoom::getId, c -> c));

        List<ClassRoom> pagedClasses = pagedIds.stream()
                .map(classMap::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        // 7. Resolve status, map to DTOs, populate enrollments
        Map<String, String> statusMap = statusResolver.resolveBatch(pagedClasses, profileId);

        List<ClassRoomResponse> responses = pagedClasses.stream()
                .map(c -> {
                    ClassRoomResponse res = classMapper.toResponse(c);
                    res.setUserStatus(statusMap.get(c.getId()));
                    return res;
                })
                .collect(Collectors.toList());

        populateEnrollmentCounts(responses);

        return PageResponse.<ClassRoomResponse>builder()
                .currentPage(page)
                .totalPages((int) Math.ceil((double) totalElements / size))
                .pageSize(size)
                .totalElements(totalElements)
                .data(responses)
                .build();
    }

    private String sanitizeHtml(String html) {
        if (html == null) return null;
        return Jsoup.clean(html, Safelist.relaxed());
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

    private void populateEnrollmentCounts(List<ClassRoomResponse> responses) {
        if (responses.isEmpty()) return;
        List<String> ids = responses.stream().map(ClassRoomResponse::getId).toList();
        List<Object[]> counts = enrollmentRepository.countByClassRoomIdIn(ids);
        Map<String, Integer> countMap = counts.stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0],
                        row -> ((Long) row[1]).intValue()
                ));
        responses.forEach(res -> res.setNumberOfStudent(countMap.getOrDefault(res.getId(), 0)));
    }
}
