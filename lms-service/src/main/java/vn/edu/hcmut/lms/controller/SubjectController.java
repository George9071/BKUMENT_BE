package vn.edu.hcmut.lms.controller;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.hcmut.lms.dto.response.APIResponse;
import vn.edu.hcmut.lms.dto.response.SubjectResponse;
import vn.edu.hcmut.lms.dto.response.TopicResponse;
import vn.edu.hcmut.lms.service.SubjectService;

import java.util.List;

@RestController
@RequestMapping("/subjects")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SubjectController {
    SubjectService subjectService;

    @GetMapping
    public APIResponse<List<SubjectResponse>> getAllSubjects() {
        return APIResponse.<List<SubjectResponse>>builder()
                .result(subjectService.getAllSubjects())
                .build();
    }

    @GetMapping("/{subjectId}/topics")
    public APIResponse<List<TopicResponse>> getTopicsBySubject(@PathVariable String subjectId) {
        return APIResponse.<List<TopicResponse>>builder()
                .result(subjectService.getAllTopicsBySubject(subjectId))
                .build();
    }
}
