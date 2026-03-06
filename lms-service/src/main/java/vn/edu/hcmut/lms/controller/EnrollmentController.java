package vn.edu.hcmut.lms.controller;

import io.swagger.v3.oas.annotations.Operation;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.*;
import vn.edu.hcmut.lms.dto.response.APIResponse;
import vn.edu.hcmut.lms.service.EnrollmentService;

@RestController
@RequestMapping("/enrollments")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class EnrollmentController {
    EnrollmentService enrollmentService;

    @PutMapping("/{enrollmentId}/approval")
    APIResponse<String> approveEnrollment(@PathVariable String enrollmentId, @RequestParam boolean approved) {
        enrollmentService.approveEnrollment(enrollmentId, approved);
        return APIResponse.<String>builder()
                .result(approved ? "Enrollment approved" : "Enrollment rejected")
                .build();
    }

    // Gia sư xóa học viên khỏi lớp (Kick)
    @DeleteMapping("/{enrollmentId}")
    APIResponse<String> removeStudent(@PathVariable String enrollmentId) {
        enrollmentService.removeStudent(enrollmentId);
        return APIResponse.<String>builder()
                .result("Student removed from class")
                .build();
    }
}
