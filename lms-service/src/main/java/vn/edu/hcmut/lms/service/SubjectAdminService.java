package vn.edu.hcmut.lms.service;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmut.lms.constant.AdminActionType;
import vn.edu.hcmut.lms.dto.request.SubjectCreationRequest;
import vn.edu.hcmut.lms.dto.request.TopicCreationRequest;
import vn.edu.hcmut.lms.dto.response.SubjectResponse;
import vn.edu.hcmut.lms.dto.response.TopicResponse;
import vn.edu.hcmut.lms.dto.sync.SubjectSyncRequest;
import vn.edu.hcmut.lms.dto.sync.TopicSyncRequest;
import vn.edu.hcmut.lms.entity.Subject;
import vn.edu.hcmut.lms.entity.Topic;
import vn.edu.hcmut.lms.exception.AppException;
import vn.edu.hcmut.lms.exception.ErrorCode;
import vn.edu.hcmut.lms.mapper.SubjectMapper;
import vn.edu.hcmut.lms.repository.SubjectRepository;
import vn.edu.hcmut.lms.repository.TopicRepository;
import vn.edu.hcmut.lms.utils.SecurityUtils;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class SubjectAdminService {

    SubjectRepository subjectRepository;
    TopicRepository topicRepository;
    SubjectMapper subjectMapper;

    GraphSyncService graphSyncService;
    AdminActionLogService auditLog;
    SecurityUtils securityUtils;

    @Transactional(rollbackFor = Exception.class)
    public SubjectResponse createSubject(SubjectCreationRequest request, String actorRole) {
        String actorId = securityUtils.getAccountId();
        String id = normalizeId(request.getId());
        String name = normalizeName(request.getName());

        if (subjectRepository.existsById(id)) {
            throw new AppException(ErrorCode.SUBJECT_ID_ALREADY_EXISTS);
        }
        if (subjectExistsByName(name)) {
            throw new AppException(ErrorCode.SUBJECT_ALREADY_EXISTS);
        }

        Subject subject = new Subject();
        subject.setId(id);
        subject.setName(name);
        subject = subjectRepository.save(subject);

        graphSyncService.syncSubjects(List.of(SubjectSyncRequest.builder()
                .id(subject.getId())
                .name(subject.getName())
                .build()));

        auditLog.record(
                actorId,
                actorRole,
                AdminActionType.CREATE_SUBJECT,
                "SUBJECT",
                subject.getId(),
                null,
                null,
                Map.of("id", subject.getId(), "name", subject.getName()),
                request.getNote());

        log.info("[ADMIN] Subject created: id={}, name='{}', by={}",
                subject.getId(), subject.getName(), actorId);

        return subjectMapper.toSubjectResponse(subject);
    }

    @Transactional(rollbackFor = Exception.class)
    public TopicResponse createTopic(TopicCreationRequest request, String actorRole) {
        String actorId = securityUtils.getAccountId();
        String id = normalizeId(request.getId());
        String name = normalizeName(request.getName());

        Subject parent = subjectRepository.findById(request.getSubjectId())
                .orElseThrow(() -> new AppException(ErrorCode.SUBJECT_NOT_FOUND));

        if (topicRepository.existsById(id)) {
            throw new AppException(ErrorCode.TOPIC_ID_ALREADY_EXISTS);
        }
        if (topicExistsInSubject(parent.getId(), name)) {
            throw new AppException(ErrorCode.TOPIC_ALREADY_EXISTS);
        }

        Topic topic = new Topic();
        topic.setId(id);
        topic.setName(name);
        topic.setSubject(parent);
        topic = topicRepository.save(topic);

        graphSyncService.syncTopics(List.of(TopicSyncRequest.builder()
                .id(topic.getId())
                .name(topic.getName())
                .subjectId(parent.getId())
                .build()));

        auditLog.record(
                actorId,
                actorRole,
                AdminActionType.CREATE_TOPIC,
                "TOPIC",
                topic.getId(),
                null,
                null,
                Map.of(
                        "id", topic.getId(),
                        "name", topic.getName(),
                        "subjectId", parent.getId()),
                request.getNote());

        log.info("[ADMIN] Topic created: id={}, name='{}', subject={}, by={}",
                topic.getId(), topic.getName(), parent.getId(), actorId);

        return subjectMapper.toTopicResponse(topic);
    }

    /* Private helpers */
    private String normalizeId(String raw) {
        return raw == null ? null : raw.trim();
    }

    private String normalizeName(String raw) {
        return raw == null ? null : raw.trim().replaceAll("\\s+", " ");
    }

    private boolean subjectExistsByName(String name) {
        return subjectRepository
                .findByNameContainingIgnoreCaseWithTopics(name, PageRequest.of(0, 50))
                .stream()
                .anyMatch(s -> s.getName().equalsIgnoreCase(name));
    }

    private boolean topicExistsInSubject(String subjectId, String topicName) {
        return topicRepository
                .findBySubjectIdIn(List.of(subjectId))
                .stream()
                .anyMatch(t -> t.getName().equalsIgnoreCase(topicName));
    }
}

