package vn.edu.hcmut.profile.controller;

import java.util.List;

import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import vn.edu.hcmut.profile.dto.response.APIResponse;
import vn.edu.hcmut.profile.dto.response.PageResponse;
import vn.edu.hcmut.profile.dto.response.UniversityResponse;
import vn.edu.hcmut.profile.service.UniversityService;

@RestController
@RequestMapping("/universities")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "University API", description = "Public APIs for fetching universities")
public class UniversityController {
    UniversityService universityService;

    @Operation(
            summary = "Get all universities",
            description = "Fetch a flat list of all universities without pagination.")
    @GetMapping
    public APIResponse<List<UniversityResponse>> getAllUniversities() {
        return APIResponse.<List<UniversityResponse>>builder()
                .result(universityService.getAllUniversities())
                .build();
    }

    @Operation(
            summary = "Search universities (Paginated)",
            description = "Search universities by name or abbreviation with pagination support.")
    @GetMapping("/search")
    public APIResponse<PageResponse<UniversityResponse>> searchUniversities(
            @Parameter(description = "Search keyword (name or abbreviation)") @RequestParam(required = false)
                    String query,
            @Parameter(description = "Page number (1-based index)") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "Number of records per page") @RequestParam(defaultValue = "10") int size) {

        return APIResponse.<PageResponse<UniversityResponse>>builder()
                .result(universityService.searchUniversities(query, page, size))
                .build();
    }
}
