package vn.edu.hcmut.profile.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import vn.edu.hcmut.profile.dto.response.PageResponse;
import vn.edu.hcmut.profile.dto.response.UniversityResponse;
import vn.edu.hcmut.profile.service.UniversityService;

@RestController
@RequestMapping("/internal/universities")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class InternalUniversityController {
    UniversityService universityService;

    @GetMapping("/search")
    public PageResponse<UniversityResponse> searchUniversities(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return universityService.searchUniversities(q, page, size);
    }
}
