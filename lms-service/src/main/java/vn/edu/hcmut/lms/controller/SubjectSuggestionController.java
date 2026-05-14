package vn.edu.hcmut.lms.controller;

import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.edu.hcmut.lms.constant.SuggestionStatus;
import vn.edu.hcmut.lms.constant.SuggestionType;
import vn.edu.hcmut.lms.dto.request.SubjectSuggestionRequest;
import vn.edu.hcmut.lms.dto.request.SuggestionDecisionRequest;
import vn.edu.hcmut.lms.dto.response.APIResponse;
import vn.edu.hcmut.lms.dto.response.PageResponse;
import vn.edu.hcmut.lms.dto.response.SubjectSuggestionResponse;
import vn.edu.hcmut.lms.service.SubjectSuggestionService;
import vn.edu.hcmut.lms.utils.SecurityUtils;

/**
 * Authorization:
 *   POST   /suggestions              — TUTOR | LEARNER.
 *   GET    /suggestions/me
 *   GET    /suggestions              — ADMIN | MODERATOR.
 *   POST   /suggestions/{id}/approve — ADMIN | MODERATOR.
 *   POST   /suggestions/{id}/reject  — ADMIN | MODERATOR.
 */

@RestController
@RequestMapping("/suggestions")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SubjectSuggestionController {
    SubjectSuggestionService suggestionService;
    SecurityUtils securityUtils;

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public APIResponse<SubjectSuggestionResponse> submit(
            @Valid @RequestBody SubjectSuggestionRequest request) {

        return APIResponse.<SubjectSuggestionResponse>builder()
                .result(suggestionService.submitSuggestion(request))
                .build();
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public APIResponse<PageResponse<SubjectSuggestionResponse>> getMine(
            @RequestParam(required = false) SuggestionStatus status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {

        return APIResponse.<PageResponse<SubjectSuggestionResponse>>builder()
                .result(suggestionService.getMySuggestions(status, page, size))
                .build();
    }

    // ──────────────────────────────────────────────────────────────────────
    //  ADMIN / MODERATOR ENDPOINTS
    // ──────────────────────────────────────────────────────────────────────

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MODERATOR')")
    public APIResponse<PageResponse<SubjectSuggestionResponse>> list(
            @RequestParam(required = false) SuggestionStatus status,
            @RequestParam(required = false) SuggestionType type,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {

        return APIResponse.<PageResponse<SubjectSuggestionResponse>>builder()
                .result(suggestionService.getSuggestions(status, type, page, size))
                .build();
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('ADMIN', 'MODERATOR')")
    public APIResponse<SubjectSuggestionResponse> approve(
            @PathVariable("id") String id,
            @Valid @RequestBody(required = false) SuggestionDecisionRequest request) {

        String role = securityUtils.getPrimaryAdminRole();
        return APIResponse.<SubjectSuggestionResponse>builder()
                .result(suggestionService.approveSuggestion(id, request, role))
                .build();
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('ADMIN', 'MODERATOR')")
    public APIResponse<SubjectSuggestionResponse> reject(
            @PathVariable("id") String id,
            @Valid @RequestBody SuggestionDecisionRequest request) {

        String role = securityUtils.getPrimaryAdminRole();
        return APIResponse.<SubjectSuggestionResponse>builder()
                .result(suggestionService.rejectSuggestion(id, request, role))
                .build();
    }
}
