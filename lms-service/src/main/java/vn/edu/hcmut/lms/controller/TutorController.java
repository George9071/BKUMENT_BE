package vn.edu.hcmut.lms.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.*;
import vn.edu.hcmut.lms.dto.request.TutorRegistrationRequest;
import vn.edu.hcmut.lms.dto.request.TutorUpdateRequest;
import vn.edu.hcmut.lms.dto.response.APIResponse;
import vn.edu.hcmut.lms.dto.response.PageResponse;
import vn.edu.hcmut.lms.dto.response.SubjectResponse;
import vn.edu.hcmut.lms.dto.response.TutorResponse;
import vn.edu.hcmut.lms.service.TutorService;

@RestController
@RequestMapping("/tutors")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Tutor management", description = "APIs for tutor registration, profile management, and searching")
public class TutorController {
    TutorService tutorService;

    @Operation(summary = "Register as a tutor",
            description = "Registers the currently authenticated user as a tutor.")
    @PostMapping("/registration")
    APIResponse<TutorResponse> registerTutor(
            @RequestBody @Valid TutorRegistrationRequest request) {
        return APIResponse.<TutorResponse>builder()
                .result(tutorService.registerTutor(request))
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
