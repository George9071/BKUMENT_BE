package vn.edu.hcmut.lms.controller.internal;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.hcmut.lms.dto.request.internal.InternalClassRatingRequest;
import vn.edu.hcmut.lms.service.ClassRoomService;

@RestController
@RequestMapping("/internal/classes")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class InternalClassRoomController {
    ClassRoomService classRoomService;

    @PostMapping("/{classId}/rating")
    public void updateClassRating(
            @PathVariable String classId,
            @RequestBody InternalClassRatingRequest request) {
        classRoomService.updateClassRating(classId, request);
    }
}
