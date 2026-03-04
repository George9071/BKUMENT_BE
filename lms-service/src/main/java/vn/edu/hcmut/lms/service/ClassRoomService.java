package vn.edu.hcmut.lms.service;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmut.lms.constant.ClassStatus;
import vn.edu.hcmut.lms.constant.LearningFormat;
import vn.edu.hcmut.lms.dto.request.ClassRoomCreationRequest;
import vn.edu.hcmut.lms.dto.request.ClassRoomUpdateRequest;
import vn.edu.hcmut.lms.dto.response.ClassRoomResponse;
import vn.edu.hcmut.lms.dto.response.ProfileResponse;
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
import vn.edu.hcmut.lms.repository.httpclient.ProfileClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
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

    @Transactional
    public ClassRoomResponse createClass(ClassRoomCreationRequest request) {
        String profileId = getProfileIdFromToken();

        Tutor tutor = tutorRepository.findById(profileId)
                .orElseThrow(() -> new AppException(ErrorCode.TUTOR_NOT_FOUND));

        ClassRoom classRoom = classMapper.toClassRoom(request);
        classRoom.setTutor(tutor);
        classRoom.setStatus(ClassStatus.ENROLLING);

        if (request.getTopicId() != null) {
            Topic topic = topicRepository.findById(request.getTopicId())
                    .orElseThrow(() -> new AppException(ErrorCode.TOPIC_NOT_FOUND));
            classRoom.setTopic(topic);
        }

        if (classRoom.getSchedules() != null) {
            for (var schedule : classRoom.getSchedules()) {
                schedule.setClassRoom(classRoom);
            }
        }

        // VALIDATION
        validationService.validateBusySchedule(profileId, classRoom);

        classRoom = classRoomRepository.save(classRoom);

        return classMapper.toResponse(classRoom);
    }

    public List<ClassRoomResponse> getMyClasses() {
        String profileId = getProfileIdFromToken();
        return classRoomRepository.findByTutorId(profileId).stream()
                .map(classMapper::toResponse)
                .toList();
    }

    public List<ClassRoomResponse> getClassesOfTutor(String tutorId) {
        return classRoomRepository.findByTutorId(tutorId).stream()
                .map(classMapper::toResponse)
                .toList();
    }

    @Transactional
    public ClassRoomResponse updateClass(String classId, ClassRoomUpdateRequest request) {
        String profileId = getProfileIdFromToken();

        ClassRoom classRoom = classRoomRepository.findById(classId)
                .orElseThrow(() -> new AppException(ErrorCode.CLASS_NOT_FOUND));

        if (!classRoom.getTutor().getId().equals(profileId)) {
            throw new AppException(ErrorCode.UNAUTHORIZED_ACTION);
        }

        // 3. Map dữ liệu update
        classMapper.updateClass(classRoom, request);

        if (request.getTopicId() != null) {
            Topic topic = topicRepository.findById(request.getTopicId())
                    .orElseThrow(() -> new AppException(ErrorCode.TOPIC_NOT_FOUND));
            classRoom.setTopic(topic);
        }

        return classMapper.toResponse(classRoomRepository.save(classRoom));
    }

    @Transactional
    public void deleteClass(String classId) {
        String profileId = getProfileIdFromToken();

        ClassRoom classRoom = classRoomRepository.findById(classId)
                .orElseThrow(() -> new AppException(ErrorCode.CLASS_NOT_FOUND));

        if (!classRoom.getTutor().getId().equals(profileId)) {
            throw new AppException(ErrorCode.UNAUTHORIZED_ACTION);
        }

        classRoom.setStatus(ClassStatus.CANCELLED);
        classRoomRepository.save(classRoom);
    }

    public List<TutorSearchResponse> searchClassesGroupedByTutor(
            String subjectName,
            String topicName,
            LearningFormat format) {

        String subject = processKeyword(subjectName);
        String topic = processKeyword(topicName);

        List<ClassRoom> matchingClasses = classRoomRepository.searchAvailableClasses(subject, topic, format);
        if (matchingClasses.isEmpty()) return new ArrayList<>();

        // Group by tutor ID
        Map<String, List<ClassRoom>> classes = matchingClasses
                .stream()
                .collect(Collectors.groupingBy(classRoom -> classRoom.getTutor().getId()));

        List<TutorSearchResponse> result = new ArrayList<>();

        for (var entry : classes.entrySet()) {
            List<ClassRoom> tutorClasses = entry.getValue();

            // Get the tutor from the first class (all classes in this list share the same tutor).
            var tutor = tutorClasses.getFirst().getTutor();

            TutorResponse response = TutorResponse.builder()
                    .id(tutor.getId())
                    .introduction(tutor.getIntroduction())
                    .averageRating(tutor.getAverageRating())
                    .ratingCount(tutor.getRatingCount())
                    .status(tutor.getStatus())
                    .name(tutor.getName())
                    .avatar(tutor.getAvatar())
                    .build();

            List<ClassRoomResponse> classResponses = tutorClasses.stream()
                    .map(classMapper::toResponse)
                    .toList();

            TutorSearchResponse searchResponse = TutorSearchResponse.builder()
                    .tutor(response)
                    .matchingClasses(classResponses)
                    .build();

            result.add(searchResponse);
        }

        return result;
    }

    private String getProfileIdFromToken() {
        var jwt = (Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return jwt.getClaimAsString("profile_id");
    }

    private String processKeyword(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return null;
        }
        return "%" + keyword.trim().toLowerCase() + "%";
    }
}
