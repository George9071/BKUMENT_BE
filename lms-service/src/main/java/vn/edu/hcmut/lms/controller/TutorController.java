package vn.edu.hcmut.lms.controller;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.*;
import vn.edu.hcmut.lms.dto.request.TutorRegistrationRequest;
import vn.edu.hcmut.lms.dto.request.TutorUpdateRequest;
import vn.edu.hcmut.lms.dto.response.APIResponse;
import vn.edu.hcmut.lms.dto.response.TutorResponse;
import vn.edu.hcmut.lms.service.TutorService;

import java.util.List;

@RestController
@RequestMapping("/tutors")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class TutorController {
    TutorService tutorService;

    @PostMapping("/registration")
    APIResponse<TutorResponse> registerTutor(@RequestBody TutorRegistrationRequest request) {
        return APIResponse.<TutorResponse>builder()
                .result(tutorService.registerTutor(request))
                .build();
    }

    @PatchMapping("/update/me")
    TutorResponse updateMyTutorProfile(@RequestBody TutorUpdateRequest request) {
        return tutorService.updateTutorProfile(request);
    }

    @GetMapping
    List<TutorResponse> getTutors(@RequestParam(required = false) String subjectId) {
        return tutorService.getTutors(subjectId);
    }
}
