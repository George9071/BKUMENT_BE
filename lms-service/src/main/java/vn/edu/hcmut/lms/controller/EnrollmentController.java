package vn.edu.hcmut.lms.controller;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.*;
import vn.edu.hcmut.lms.dto.response.APIResponse;
import vn.edu.hcmut.lms.dto.response.EnrollmentResponse;
import vn.edu.hcmut.lms.service.EnrollmentService;

import java.util.List;

@RestController
@RequestMapping("/enrollments")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class EnrollmentController {
    EnrollmentService enrollmentService;

    @PostMapping("/class/{classId}")
    APIResponse<EnrollmentResponse> enrollClass(@PathVariable String classId) {
        return APIResponse.<EnrollmentResponse>builder()
                .result(enrollmentService.enrollClass(classId))
                .build();
    }

    // Gia sư duyệt hoặc từ chối đơn đăng ký
    // URL query: ?approved=true hoặc ?approved=false
    @PutMapping("/{enrollmentId}/approval")
    APIResponse<String> approveEnrollment(@PathVariable String enrollmentId, @RequestParam boolean approved) {
        enrollmentService.approveEnrollment(enrollmentId, approved);
        return APIResponse.<String>builder()
                .result(approved ? "Enrollment approved" : "Enrollment rejected")
                .build();
    }

    @GetMapping("/class/{classId}")
    APIResponse<List<EnrollmentResponse>> getClassMembers(@PathVariable String classId) {
        return APIResponse.<List<EnrollmentResponse>>builder()
                .result(enrollmentService.getClassMembers(classId))
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
