package vn.edu.hcmut.lms.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;
import vn.edu.hcmut.lms.constant.ApplicationStatus;
import vn.edu.hcmut.lms.dto.request.TutorRegistrationRequest;
import vn.edu.hcmut.lms.dto.request.TutorUpdateRequest;
import vn.edu.hcmut.lms.dto.response.*;
import vn.edu.hcmut.lms.service.TutorApplicationService;
import vn.edu.hcmut.lms.service.TutorService;

@RestController
@RequestMapping("/tutors")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Tutor management", description = "APIs for tutor registration, profile management, and searching")
public class TutorController {
    TutorService tutorService;
    TutorApplicationService applicationService;

    @Operation(summary = "Register as a tutor",
            description = "Submit an application to become a tutor. Only one PENDING application is allowed at a time.")
    @PostMapping("/registration")
    public APIResponse<ApplicationResponse> registerTutor(
            @RequestBody @Valid TutorRegistrationRequest request) {

        return APIResponse.<ApplicationResponse>builder()
                .result(applicationService.registerTutor(request))
                .build();
    }

    @Operation(summary = "Get my tutor application",
            description = "Retrieves the tutor application submitted by the currently authenticated user.")
    @GetMapping("/registration/me")
    public APIResponse<ApplicationResponse> getMyApplication() {
        return APIResponse.<ApplicationResponse>builder()
                .result(applicationService.getMyApplication())
                .build();
    }

    @Operation(summary = "Get list of tutor applications (Admin)",
            description = "Retrieve a paginated list of applications. Can filter by status (PENDING, APPROVED, REJECTED).")
    @GetMapping("/admin/applications")
    public APIResponse<PageResponse<ApplicationResponse>> getApplications(
            @RequestParam(required = false) ApplicationStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        return APIResponse.<PageResponse<ApplicationResponse>>builder()
                .result(applicationService.getApplications(status, page, size))
                .build();
    }

    @Operation(summary = "Approve a tutor application (Admin)",
            description = "Approves the application, creates a Tutor profile, and grants TUTOR role.")
    @PostMapping("/admin/applications/{applicationId}/approve")
    public APIResponse<String> approveApplication(@PathVariable String applicationId) {

        applicationService.approveApplication(applicationId);

        return APIResponse.<String>builder()
                .result("The application has been successfully approved..")
                .build();
    }

    @Operation(summary = "Reject a tutor application (Admin)",
            description = "Rejects the application and logs the rejection reason.")
    @PostMapping("/admin/applications/{applicationId}/reject")
    public APIResponse<String> rejectApplication(
            @PathVariable String applicationId,
            @RequestBody @Valid String reason) {

        applicationService.rejectApplication(applicationId, reason);

        return APIResponse.<String>builder()
                .result("The application was rejected.")
                .build();
    }

    @Operation(summary = "Update my tutor profile",
            description = "Updates the tutor profile information of the current user.")
    @PatchMapping("/me")
    public APIResponse<TutorResponse> updateMyTutorProfile(
            @RequestBody @Valid TutorUpdateRequest request) {
        return APIResponse.<TutorResponse>builder()
                .result(tutorService.updateTutorProfile(request))
                .build();
    }

    @Operation(summary = "Get my tutor profile",
            description = "Retrieves the tutor profile of the currently authenticated user.")
    @GetMapping("/me")
    public APIResponse<TutorResponse> getMyTutorProfile() {
        return APIResponse.<TutorResponse>builder()
                .result(tutorService.getMyTutorProfile())
                .build();
    }

    @Operation(summary = "Search tutors",
            description = "Retrieves a paginated list of active tutors. Can be optionally filtered by subject.")
    @GetMapping
    public APIResponse<PageResponse<TutorResponse>> getTutors(
            @Parameter(description = "Filter tutors by subject ID") @RequestParam(required = false) String subjectId,
            @Parameter(description = "Page number") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "10") int size) {
        return APIResponse.<PageResponse<TutorResponse>>builder()
                .result(tutorService.getTutors(subjectId, page, size))
                .build();
    }

    @Operation(summary = "Get my teaching subjects",
            description = "Retrieves a paginated list of subjects registered by the current tutor.")
    @GetMapping("/me/subjects")
    public APIResponse<PageResponse<SubjectResponse>> getTutorSubjects(
            @Parameter(description = "Page number") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "10") int size) {
        return APIResponse.<PageResponse<SubjectResponse>>builder()
                .result(tutorService.getTutorSubjects(page, size))
                .build();
    }
}
