package vn.edu.hcmut.social.controller;

import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import vn.edu.hcmut.social.dto.request.ReportRequest;
import vn.edu.hcmut.social.dto.request.ReportStatusUpdateRequest;
import vn.edu.hcmut.social.dto.response.APIResponse;
import vn.edu.hcmut.social.dto.response.ReportResponse;
import vn.edu.hcmut.social.exception.AppException;
import vn.edu.hcmut.social.exception.ErrorCode;
import vn.edu.hcmut.social.service.ReportService;

@RestController
@RequestMapping("/reports")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Report", description = "Report APIs for resources and users")
public class ReportController {
    ReportService reportService;

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

    @PostMapping
    public APIResponse<ReportResponse> createReport(@RequestBody @Valid ReportRequest request) {
        String reporterId = getProfileIdFromToken();
        return APIResponse.<ReportResponse>builder()
                .result(reportService.createReport(request, reporterId))
                .message("Report created successfully")
                .build();
    }

    @PutMapping("/{reportId}/status")
    public APIResponse<ReportResponse> updateReportStatus(
            @PathVariable String reportId, @RequestBody @Valid ReportStatusUpdateRequest request) {
        String resolverId = getProfileIdFromToken();
        return APIResponse.<ReportResponse>builder()
                .result(reportService.updateReportStatus(reportId, request.getStatus(), resolverId))
                .message("Report status updated successfully")
                .build();
    }

    @DeleteMapping("/{reportId}")
    public APIResponse<String> deleteReport(@PathVariable String reportId) {
        reportService.deleteReport(reportId);
        return APIResponse.<String>builder()
                .message("Report deleted successfully")
                .build();
    }

    @GetMapping
    public APIResponse<Page<ReportResponse>> getAllReports(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        Pageable pageable = PageRequest.of(page, size);

        return APIResponse.<Page<ReportResponse>>builder()
                .result(reportService.getAllReports(status, pageable))
                .message("Get reports successfully")
                .build();
    }
}
