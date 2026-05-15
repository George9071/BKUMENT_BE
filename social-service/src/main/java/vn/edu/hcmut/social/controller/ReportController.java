package vn.edu.hcmut.social.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import vn.edu.hcmut.social.dto.request.ReportRequest;
import vn.edu.hcmut.social.dto.response.APIResponse;
import vn.edu.hcmut.social.dto.response.ContentResponse;
import vn.edu.hcmut.social.dto.response.ReportResponse;
import vn.edu.hcmut.social.enums.ReportStatus;
import vn.edu.hcmut.social.exception.AppException;
import vn.edu.hcmut.social.exception.ErrorCode;
import vn.edu.hcmut.social.service.ReportService;

@Slf4j
@RestController
@RequestMapping("/reports")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Report", description = "Report APIs for resources and users")
public class ReportController {

    static final int MAX_PAGE_SIZE = 50;

    ReportService reportService;

    @GetMapping("/blogs")
    // @PreAuthorize("hasRole('ADMIN') or hasRole('MODERATOR')")
    public APIResponse<Page<ContentResponse>> getReportedBlogs(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(MAX_PAGE_SIZE) int size) {
        checkAdminRole();
        Pageable pageable = PageRequest.of(page, size);
        Page<ContentResponse> result = reportService.getReportedBlogs(status, pageable);

        return APIResponse.<Page<ContentResponse>>builder()
                .message("Reported blogs fetched")
                .result(result)
                .build();
    }

    @GetMapping("/documents")
    // @PreAuthorize("hasRole('ADMIN') or hasRole('MODERATOR')")
    public APIResponse<Page<ContentResponse>> getReportedDocuments(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(MAX_PAGE_SIZE) int size) {
        checkAdminRole();
        Pageable pageable = PageRequest.of(page, size);
        Page<ContentResponse> result = reportService.getReportedDocuments(status, pageable);

        return APIResponse.<Page<ContentResponse>>builder()
                .code(1000)
                .message("Reported documents fetched")
                .result(result)
                .build();
    }

    @PostMapping
    public APIResponse<ReportResponse> createReport(@RequestBody @Valid ReportRequest request) {
        String reporterId = getProfileIdFromToken();
        return APIResponse.<ReportResponse>builder()
                .result(reportService.createReport(request, reporterId))
                .message("Report created successfully")
                .build();
    }

    @PutMapping("/{reportId}/status")
    // @PreAuthorize("hasRole('ADMIN') or hasRole('MODERATOR')")
    public APIResponse<ReportResponse> updateReportStatus(
            @PathVariable @NotBlank String reportId, @RequestParam String status) {

        ReportStatus newStatus;
        try {
            newStatus = ReportStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new AppException(ErrorCode.INVALID_REPORT_STATUS);
        }
        if (newStatus == ReportStatus.PENDING) {
            throw new AppException(ErrorCode.INVALID_REPORT_STATUS);
        }

        String resolverId = getCurrentJwt().getSubject();
        ReportResponse updated = reportService.updateReportStatus(reportId, newStatus, resolverId);
        log.info("Report {} {} by {}", reportId, newStatus, resolverId);

        return APIResponse.<ReportResponse>builder()
                .message("Report status updated")
                .result(updated)
                .build();
    }

    @DeleteMapping("/{reportId}")
    public APIResponse<String> deleteReport(@PathVariable String reportId) {
        checkAdminRole();
        reportService.deleteReport(reportId);
        return APIResponse.<String>builder()
                .message("Report deleted successfully")
                .build();
    }

    private Jwt getCurrentJwt() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof Jwt jwt)) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        return jwt;
    }

    private void checkAdminRole() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        boolean isAuthorized = authentication.getAuthorities().stream()
                .anyMatch(grantedAuthority -> {
                    String auth = grantedAuthority.getAuthority();
                    return auth.equals("ROLE_ADMIN") || auth.equals("ADMIN") || auth.equals("SCOPE_ADMIN")
                            || auth.equals("ROLE_MODERATOR") || auth.equals("MODERATOR") || auth.equals("SCOPE_MODERATOR");
                });

        if (!isAuthorized) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
    }

    private String getProfileIdFromToken() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            Jwt jwt = jwtAuth.getToken();

            String profileId = jwt.getClaimAsString("profile_id");
            if (profileId == null || profileId.isBlank()) {
                throw new AppException(ErrorCode.INVALID_TOKEN_CLAIMS);
            }

            return profileId;
        }

        throw new AppException(ErrorCode.UNAUTHENTICATED);
    }
}
