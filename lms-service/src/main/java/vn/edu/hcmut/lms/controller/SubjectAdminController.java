package vn.edu.hcmut.lms.controller;

import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.edu.hcmut.lms.constant.AdminActionType;
import vn.edu.hcmut.lms.dto.request.SubjectCreationRequest;
import vn.edu.hcmut.lms.dto.request.TopicCreationRequest;
import vn.edu.hcmut.lms.dto.response.*;
import vn.edu.hcmut.lms.service.AdminActionLogService;
import vn.edu.hcmut.lms.service.SubjectAdminService;
import vn.edu.hcmut.lms.utils.SecurityUtils;

@RestController
@RequestMapping("/administration")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@PreAuthorize("hasAnyRole('ADMIN', 'MODERATOR')")
public class SubjectAdminController {

    SubjectAdminService subjectAdminService;
    AdminActionLogService auditLogService;
    SecurityUtils securityUtils;

    @PostMapping("/subjects")
    public APIResponse<SubjectResponse> createSubject(
            @Valid @RequestBody SubjectCreationRequest request) {

        String role = securityUtils.getPrimaryAdminRole();
        return APIResponse.<SubjectResponse>builder()
                .result(subjectAdminService.createSubject(request, role))
                .build();
    }

    @PostMapping("/topics")
    public APIResponse<TopicResponse> createTopic(
            @Valid @RequestBody TopicCreationRequest request) {

        String role = securityUtils.getPrimaryAdminRole();
        return APIResponse.<TopicResponse>builder()
                .result(subjectAdminService.createTopic(request, role))
                .build();
    }

    /**
     * Truy vấn audit log với các filter tuỳ chọn (mutually exclusive theo độ ưu tiên
     * mô tả trong AdminActionLogService).
     */
    @GetMapping("/audit-logs")
    public APIResponse<PageResponse<AdminActionLogResponse>> getAuditLogs(
            @RequestParam(required = false) String actorId,
            @RequestParam(required = false) AdminActionType action,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) String targetId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {

        return APIResponse.<PageResponse<AdminActionLogResponse>>builder()
                .result(auditLogService.queryLogs(actorId, action, targetType, targetId, page, size))
                .build();
    }
}
