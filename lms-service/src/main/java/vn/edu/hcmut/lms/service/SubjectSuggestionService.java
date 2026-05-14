package vn.edu.hcmut.lms.service;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmut.lms.constant.AdminActionType;
import vn.edu.hcmut.lms.constant.SuggestionStatus;
import vn.edu.hcmut.lms.constant.SuggestionType;
import vn.edu.hcmut.lms.dto.request.SubjectSuggestionRequest;
import vn.edu.hcmut.lms.dto.request.SuggestionDecisionRequest;
import vn.edu.hcmut.lms.dto.response.PageResponse;
import vn.edu.hcmut.lms.dto.response.SubjectSuggestionResponse;
import vn.edu.hcmut.lms.dto.sync.SubjectSyncRequest;
import vn.edu.hcmut.lms.dto.sync.TopicSyncRequest;
import vn.edu.hcmut.lms.entity.Subject;
import vn.edu.hcmut.lms.entity.SubjectSuggestion;
import vn.edu.hcmut.lms.entity.Topic;
import vn.edu.hcmut.lms.exception.AppException;
import vn.edu.hcmut.lms.exception.ErrorCode;
import vn.edu.hcmut.lms.repository.SubjectRepository;
import vn.edu.hcmut.lms.repository.SubjectSuggestionRepository;
import vn.edu.hcmut.lms.repository.TopicRepository;
import vn.edu.hcmut.lms.utils.SecurityUtils;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service processes new subject/topic suggestions.
 * 2 main flows:
 * 1. submit_suggestion()   — tutor/learner submits a suggestion form. Anti-abuse: 1 PENDING / (user, type, name).
 * 2. approve_suggestion()  — admin/moderator approves: creates a real subject/topic entity
 *    reject_suggestion()   — admin/moderator rejects with a reason.
 * * * *
 * Audit log:
 * All mutations are recorded via admin_action_log_service with before/after snapshots.
 * Suggestion APPROVED generates 2 log entries: APPROVE_SUGGESTION + CREATE_SUBJECT/TOPIC.
 * (Separates the two entries to query "who approved suggestion X" and "who created subject Y" independently.)
 */

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class SubjectSuggestionService {

    SubjectSuggestionRepository suggestionRepository;
    SubjectRepository subjectRepository;
    TopicRepository topicRepository;

    GraphSyncService graphSyncService;
    AdminActionLogService auditLog;
    SecurityUtils securityUtils;

    @Transactional(rollbackFor = Exception.class)
    public SubjectSuggestionResponse submitSuggestion(SubjectSuggestionRequest request) {
        String reporterId = securityUtils.getProfileId();
        String name = normalizeName(request.getProposedName());

        if (request.getType() == SuggestionType.TOPIC) {
            if (request.getParentSubjectId() == null || request.getParentSubjectId().isBlank()) {
                throw new AppException(ErrorCode.PARENT_SUBJECT_REQUIRED);
            }
            if (!subjectRepository.existsById(request.getParentSubjectId())) {
                throw new AppException(ErrorCode.SUBJECT_NOT_FOUND);
            }
        }

        if (request.getType() == SuggestionType.SUBJECT && subjectExistsByName(name)) {
            throw new AppException(ErrorCode.SUBJECT_ALREADY_EXISTS);
        }
        if (request.getType() == SuggestionType.TOPIC
                && topicExistsInSubject(request.getParentSubjectId(), name)) {
            throw new AppException(ErrorCode.TOPIC_ALREADY_EXISTS);
        }

        boolean duplicate = suggestionRepository
                .existsByReporterIdAndTypeAndProposedNameIgnoreCaseAndStatus(
                        reporterId, request.getType(), name, SuggestionStatus.PENDING);
        if (duplicate) throw new AppException(ErrorCode.SUGGESTION_ALREADY_SUBMITTED);

        SubjectSuggestion suggestion = SubjectSuggestion.builder()
                .reporterId(reporterId)
                .type(request.getType())
                .proposedName(name)
                .parentSubjectId(
                        request.getType() == SuggestionType.TOPIC
                                ? request.getParentSubjectId()
                                : null)
                .reason(request.getReason())
                .status(SuggestionStatus.PENDING)
                .build();

        suggestion = suggestionRepository.save(suggestion);

        log.info("[SUGGESTION] New {} suggestion by {}: '{}' (id={})",
                request.getType(), reporterId, name, suggestion.getId());

        return toResponse(suggestion);
    }

    @Transactional(readOnly = true)
    public PageResponse<SubjectSuggestionResponse> getMySuggestions(
            SuggestionStatus status, int page, int size) {
        String reporterId = securityUtils.getProfileId();
        Pageable pageable = pageable(page, size);

        Page<SubjectSuggestion> result = (status == null)
                ? suggestionRepository.findByReporterId(reporterId, pageable)
                : suggestionRepository.findByReporterIdAndStatus(reporterId, status, pageable);

        return toPageResponse(result, page);
    }

    @Transactional(readOnly = true)
    public PageResponse<SubjectSuggestionResponse> getSuggestions(
            SuggestionStatus status, SuggestionType type, int page, int size) {

        Pageable pageable = pageable(page, size);

        Page<SubjectSuggestion> result;
        if (status != null && type != null) {
            result = suggestionRepository.findByStatusAndType(status, type, pageable);
        } else if (status != null) {
            result = suggestionRepository.findByStatus(status, pageable);
        } else {
            result = suggestionRepository.findAll(pageable);
        }

        return toPageResponse(result, page);
    }

    @Transactional(rollbackFor = Exception.class)
    public SubjectSuggestionResponse approveSuggestion(
            String suggestionId, SuggestionDecisionRequest request, String actorRole) {

        if (request == null || request.getFinalId() == null || request.getFinalId().isBlank()) {
            throw new AppException(ErrorCode.FINAL_ID_REQUIRED);
        }
        String finalId = normalizeId(request.getFinalId());

        String actorId = securityUtils.getAccountId();
        SubjectSuggestion suggestion = lockPending(suggestionId);

        String finalName = (request.getFinalName() != null && !request.getFinalName().isBlank())
                ? normalizeName(request.getFinalName())
                : suggestion.getProposedName();

        String parentSubjectId = suggestion.getParentSubjectId();
        if (suggestion.getType() == SuggestionType.TOPIC
                && request.getParentSubjectId() != null
                && !request.getParentSubjectId().isBlank()) {
            parentSubjectId = request.getParentSubjectId();
        }

        if (suggestion.getType() == SuggestionType.SUBJECT) {
            if (subjectRepository.existsById(finalId)) {
                throw new AppException(ErrorCode.SUBJECT_ID_ALREADY_EXISTS);
            }
            if (subjectExistsByName(finalName)) {
                throw new AppException(ErrorCode.SUBJECT_ALREADY_EXISTS);
            }
        } else {
            if (topicRepository.existsById(finalId)) {
                throw new AppException(ErrorCode.TOPIC_ID_ALREADY_EXISTS);
            }
            if (topicExistsInSubject(parentSubjectId, finalName)) {
                throw new AppException(ErrorCode.TOPIC_ALREADY_EXISTS);
            }
        }

        Map<String, Object> before = snapshotSuggestion(suggestion);

        String createdResourceId;
        AdminActionType createAction;
        String createTargetType;
        Map<String, Object> createdSnapshot;

        if (suggestion.getType() == SuggestionType.SUBJECT) {
            Subject subject = subjectRepository.save(buildSubject(finalId, finalName));
            createdResourceId = subject.getId();
            createAction = AdminActionType.CREATE_SUBJECT;
            createTargetType = "SUBJECT";
            createdSnapshot = Map.of("id", subject.getId(), "name", subject.getName());

            graphSyncService.syncSubjects(List.of(SubjectSyncRequest.builder()
                    .id(subject.getId())
                    .name(subject.getName())
                    .build()));
        } else {
            // type = TOPIC
            Subject parent = subjectRepository.findById(parentSubjectId)
                    .orElseThrow(() -> new AppException(ErrorCode.SUBJECT_NOT_FOUND));

            Topic topic = topicRepository.save(buildTopic(finalId, finalName, parent));
            createdResourceId = topic.getId();
            createAction = AdminActionType.CREATE_TOPIC;
            createTargetType = "TOPIC";
            createdSnapshot = Map.of(
                    "id", topic.getId(),
                    "name", topic.getName(),
                    "subjectId", parent.getId());

            graphSyncService.syncTopics(List.of(TopicSyncRequest.builder()
                    .id(topic.getId())
                    .name(topic.getName())
                    .subjectId(parent.getId())
                    .build()));
        }

        suggestion.setStatus(SuggestionStatus.APPROVED);
        suggestion.setReviewerId(actorId);
        suggestion.setReviewedAt(Instant.now());
        suggestion.setCreatedResourceId(createdResourceId);
        suggestion.setProposedName(finalName);
        if (suggestion.getType() == SuggestionType.TOPIC) {
            suggestion.setParentSubjectId(parentSubjectId);
        }
        suggestion = suggestionRepository.save(suggestion);

        Map<String, Object> after = snapshotSuggestion(suggestion);

        String note = request.getNote();

        auditLog.record(
                actorId,
                actorRole,
                AdminActionType.APPROVE_SUGGESTION,
                "SUGGESTION",
                suggestion.getId(),
                suggestion.getId(),
                before,
                after,
                note);

        auditLog.record(
                actorId,
                actorRole,
                createAction,
                createTargetType,
                createdResourceId,
                suggestion.getId(),
                null,
                createdSnapshot,
                note);

        return toResponse(suggestion);
    }

    @Transactional(rollbackFor = Exception.class)
    public SubjectSuggestionResponse rejectSuggestion(
            String suggestionId, SuggestionDecisionRequest request, String actorRole) {

        if (request == null
                || request.getRejectionReason() == null
                || request.getRejectionReason().isBlank()) {
            throw new AppException(ErrorCode.REJECTION_REASON_REQUIRED);
        }

        String actorId = securityUtils.getAccountId();
        SubjectSuggestion suggestion = lockPending(suggestionId);

        Map<String, Object> before = snapshotSuggestion(suggestion);

        suggestion.setStatus(SuggestionStatus.REJECTED);
        suggestion.setReviewerId(actorId);
        suggestion.setReviewedAt(Instant.now());
        suggestion.setRejectionReason(request.getRejectionReason());

        suggestion = suggestionRepository.save(suggestion);

        Map<String, Object> after = snapshotSuggestion(suggestion);

        auditLog.record(
                actorId,
                actorRole,
                AdminActionType.REJECT_SUGGESTION,
                "SUGGESTION",
                suggestion.getId(),
                suggestion.getId(),
                before,
                after,
                request.getNote());

        return toResponse(suggestion);
    }

    /* Private helpers */
    private SubjectSuggestion lockPending(String suggestionId) {
        SubjectSuggestion suggestion = suggestionRepository.findById(suggestionId)
                .orElseThrow(() -> new AppException(ErrorCode.SUGGESTION_NOT_FOUND));

        if (suggestion.getStatus() != SuggestionStatus.PENDING) {
            throw new AppException(ErrorCode.SUGGESTION_ALREADY_PROCESSED);
        }
        return suggestion;
    }

    private String normalizeId(String raw) {
        return raw == null ? null : raw.trim();
    }

    private String normalizeName(String raw) {
        if (raw == null) return null;
        return raw.trim().replaceAll("\\s+", " ");
    }

    private boolean subjectExistsByName(String name) {
        // SubjectRepository hiện chưa có existsByNameIgnoreCase — dùng findByNameContainingIgnoreCase
        // sẽ false-positive với substring. Dùng JPA query trực tiếp.
        return subjectRepository
                .findByNameContainingIgnoreCaseWithTopics(name, PageRequest.of(0, 50))
                .stream()
                .anyMatch(s -> s.getName().equalsIgnoreCase(name));
    }

    private boolean topicExistsInSubject(String subjectId, String topicName) {
        // Topics-per-subject thường ít. List ra rồi so sánh.
        return topicRepository
                .findBySubjectIdIn(List.of(subjectId))
                .stream()
                .anyMatch(t -> t.getName().equalsIgnoreCase(topicName));
    }

    private Subject buildSubject(String id, String name) {
        Subject s = new Subject();
        s.setId(id);
        s.setName(name);
        return s;
    }

    private Topic buildTopic(String id, String name, Subject parent) {
        Topic t = new Topic();
        t.setId(id);
        t.setName(name);
        t.setSubject(parent);
        return t;
    }

    private Map<String, Object> snapshotSuggestion(SubjectSuggestion s) {
        Map<String, Object> snap = new HashMap<>();
        snap.put("id", s.getId());
        snap.put("status", s.getStatus() != null ? s.getStatus().name() : null);
        snap.put("type", s.getType() != null ? s.getType().name() : null);
        snap.put("proposedName", s.getProposedName());
        snap.put("parentSubjectId", s.getParentSubjectId());
        snap.put("reason", s.getReason());
        snap.put("reviewerId", s.getReviewerId());
        snap.put("rejectionReason", s.getRejectionReason());
        snap.put("createdResourceId", s.getCreatedResourceId());
        return snap;
    }

    private Pageable pageable(int page, int size) {
        return PageRequest.of(
                (page > 0) ? page - 1 : 0,
                size,
                Sort.by("createdAt").descending());
    }

    private PageResponse<SubjectSuggestionResponse> toPageResponse(
            Page<SubjectSuggestion> result, int page) {
        List<SubjectSuggestionResponse> data = result.getContent().stream()
                .map(this::toResponse)
                .toList();

        return PageResponse.<SubjectSuggestionResponse>builder()
                .currentPage(page)
                .totalPages(result.getTotalPages())
                .pageSize(result.getSize())
                .totalElements(result.getTotalElements())
                .data(data)
                .build();
    }

    private SubjectSuggestionResponse toResponse(SubjectSuggestion s) {
        String parentName = null;
        if (s.getParentSubjectId() != null) {
            parentName = subjectRepository.findById(s.getParentSubjectId())
                    .map(Subject::getName)
                    .orElse(null);
        }
        return SubjectSuggestionResponse.builder()
                .id(s.getId())
                .reporterId(s.getReporterId())
                .type(s.getType())
                .proposedName(s.getProposedName())
                .parentSubjectId(s.getParentSubjectId())
                .parentSubjectName(parentName)
                .reason(s.getReason())
                .status(s.getStatus())
                .reviewerId(s.getReviewerId())
                .rejectionReason(s.getRejectionReason())
                .createdResourceId(s.getCreatedResourceId())
                .reviewedAt(s.getReviewedAt())
                .createdAt(s.getCreatedAt())
                .updatedAt(s.getUpdatedAt())
                .build();
    }
}
