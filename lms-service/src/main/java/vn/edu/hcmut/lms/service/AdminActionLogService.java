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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmut.lms.constant.AdminActionType;
import vn.edu.hcmut.lms.dto.response.AdminActionLogResponse;
import vn.edu.hcmut.lms.dto.response.PageResponse;
import vn.edu.hcmut.lms.entity.AdminActionLog;
import vn.edu.hcmut.lms.repository.AdminActionLogRepository;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class AdminActionLogService {
    AdminActionLogRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AdminActionLog record(
            String actorId,
            String actorRole,
            AdminActionType action,
            String targetType,
            String targetId,
            String sourceSuggestionId,
            Map<String, Object> beforeSnapshot,
            Map<String, Object> afterSnapshot,
            String note) {

        AdminActionLog entry = AdminActionLog.builder()
                .actorId(actorId)
                .actorRole(actorRole)
                .action(action)
                .targetType(targetType)
                .targetId(targetId)
                .sourceSuggestionId(sourceSuggestionId)
                .beforeSnapshot(beforeSnapshot)
                .afterSnapshot(afterSnapshot)
                .note(note)
                .build();

        entry = repository.save(entry);

        log.info(
                "[ADMIN-AUDIT] actor={} ({}), action={}, target={}/{}, src={}, logId={}",
                actorId, actorRole, action, targetType, targetId, sourceSuggestionId, entry.getId());

        return entry;
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminActionLogResponse> queryLogs(
            String actorId,
            AdminActionType action,
            String targetType,
            String targetId,
            int page,
            int size) {

        Pageable pageable = PageRequest.of(
                (page > 0) ? page - 1 : 0,
                size,
                Sort.by("createdAt").descending());

        Page<AdminActionLog> result;
        if (actorId != null && !actorId.isBlank()) {
            result = repository.findByActorId(actorId, pageable);
        } else if (action != null) {
            result = repository.findByAction(action, pageable);
        } else if (targetType != null && targetId != null) {
            result = repository.findByTargetTypeAndTargetId(targetType, targetId, pageable);
        } else {
            result = repository.findAll(pageable);
        }

        List<AdminActionLogResponse> data = result.getContent().stream()
                .map(this::toResponse)
                .toList();

        return PageResponse.<AdminActionLogResponse>builder()
                .currentPage(page)
                .totalPages(result.getTotalPages())
                .pageSize(result.getSize())
                .totalElements(result.getTotalElements())
                .data(data)
                .build();
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminActionLogResponse> queryLogsByRange(
            Instant from, Instant to, int page, int size) {

        Pageable pageable = PageRequest.of(
                (page > 0) ? page - 1 : 0,
                size,
                Sort.by("createdAt").descending());

        Page<AdminActionLog> result = repository.findByCreatedAtBetween(from, to, pageable);

        List<AdminActionLogResponse> data = result.getContent().stream()
                .map(this::toResponse)
                .toList();

        return PageResponse.<AdminActionLogResponse>builder()
                .currentPage(page)
                .totalPages(result.getTotalPages())
                .pageSize(result.getSize())
                .totalElements(result.getTotalElements())
                .data(data)
                .build();
    }

    private AdminActionLogResponse toResponse(AdminActionLog entry) {
        return AdminActionLogResponse.builder()
                .id(entry.getId())
                .actorId(entry.getActorId())
                .actorRole(entry.getActorRole())
                .action(entry.getAction())
                .targetType(entry.getTargetType())
                .targetId(entry.getTargetId())
                .sourceSuggestionId(entry.getSourceSuggestionId())
                .beforeSnapshot(entry.getBeforeSnapshot())
                .afterSnapshot(entry.getAfterSnapshot())
                .note(entry.getNote())
                .createdAt(entry.getCreatedAt())
                .build();
    }
}
