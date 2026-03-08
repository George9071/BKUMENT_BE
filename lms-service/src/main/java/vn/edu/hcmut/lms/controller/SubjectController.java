package vn.edu.hcmut.lms.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.*;
import vn.edu.hcmut.lms.dto.response.APIResponse;
import vn.edu.hcmut.lms.dto.response.PageResponse;
import vn.edu.hcmut.lms.dto.response.SubjectResponse;
import vn.edu.hcmut.lms.dto.response.TopicResponse;
import vn.edu.hcmut.lms.service.SubjectService;

import java.util.List;

@RestController
@RequestMapping("/subjects")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Subject management", description = "Public APIs for fetching subjects and topics")
public class SubjectController {

    SubjectService subjectService;

    @Operation(summary = "Get all subjects", description = "Retrieves a paginated list of all available subjects.")
    @GetMapping
    public APIResponse<PageResponse<SubjectResponse>> getAllSubjects(
            @Parameter(description = "Page number") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "10") int size) {
        return APIResponse.<PageResponse<SubjectResponse>>builder()
                .result(subjectService.getAllSubjects(page, size))
                .build();
    }

    @Operation(summary = "Get topics by subject",
            description = "Retrieves a paginated list of topics belonging to a specific subject.")
    @GetMapping("/{subjectId}/topics")
    public APIResponse<PageResponse<TopicResponse>> getTopicsBySubject(
            @Parameter(description = "ID of the subject") @PathVariable String subjectId,
            @Parameter(description = "Page number") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "10") int size) {
        return APIResponse.<PageResponse<TopicResponse>>builder()
                .result(subjectService.getAllTopicsBySubject(subjectId, page, size))
                .build();
    }
}
