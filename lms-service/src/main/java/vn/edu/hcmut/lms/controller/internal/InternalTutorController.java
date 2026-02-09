package vn.edu.hcmut.lms.controller.internal;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.hcmut.lms.service.TutorService;

@RestController
@RequestMapping("/internal/tutors")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class InternalTutorController {

    TutorService tutorService;

    @DeleteMapping("/{profileId}")
    public void deleteTutor(@PathVariable String profileId) {
        tutorService.deleteTutor(profileId);
    }
}
