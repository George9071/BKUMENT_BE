package vn.edu.hcmut.lms.controller;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.hcmut.lms.dto.response.APIResponse;
import vn.edu.hcmut.lms.dto.response.SubjectResponse;
import vn.edu.hcmut.lms.service.SubjectService;

import java.util.List;

@RestController
@RequestMapping("/subjects")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SubjectController {
    SubjectService subjectService;

    @GetMapping("/search")
    public APIResponse<List<SubjectResponse>> searchSubjects(@RequestParam(required = false) String q) {
        List<SubjectResponse> subjects = subjectService.searchSubjects(q);
        return APIResponse.<List<SubjectResponse>>builder()
                .result(subjects)
                .message("Get subjects successfully")
                .build();
    }
}
