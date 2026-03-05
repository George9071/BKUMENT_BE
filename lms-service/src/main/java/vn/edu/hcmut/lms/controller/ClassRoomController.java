package vn.edu.hcmut.lms.controller;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.*;
import vn.edu.hcmut.lms.constant.LearningFormat;
import vn.edu.hcmut.lms.dto.request.ClassRoomCreationRequest;
import vn.edu.hcmut.lms.dto.request.ClassRoomUpdateRequest;
import vn.edu.hcmut.lms.dto.response.APIResponse;
import vn.edu.hcmut.lms.dto.response.ClassRoomResponse;
import vn.edu.hcmut.lms.dto.response.EnrollmentResponse;
import vn.edu.hcmut.lms.dto.response.TutorSearchResponse;
import vn.edu.hcmut.lms.service.ClassRoomService;
import vn.edu.hcmut.lms.service.EnrollmentService;

import java.util.List;

@RestController
@RequestMapping("/classes")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ClassRoomController {
    ClassRoomService classRoomService;
    EnrollmentService enrollmentService;

    @PostMapping
    APIResponse<ClassRoomResponse> createClass(
            @RequestBody ClassRoomCreationRequest request) {
        return APIResponse.<ClassRoomResponse>builder()
                .result(classRoomService.createClass(request))
                .build();
    }

    @GetMapping("/my-classes")
    APIResponse<List<ClassRoomResponse>> getMyClasses() {
        return APIResponse.<List<ClassRoomResponse>>builder()
                .result(classRoomService.getMyClasses())
                .build();
    }

    @GetMapping("/class/{tutorId}")
    APIResponse<List<ClassRoomResponse>> getClassesOfTutor(@PathVariable String tutorId) {
        return APIResponse.<List<ClassRoomResponse>>builder()
                .result(classRoomService.getClassesOfTutor(tutorId))
                .build();
    }

    @PutMapping("/{classId}")
    APIResponse<ClassRoomResponse> updateClass(
            @PathVariable String classId,
            @RequestBody ClassRoomUpdateRequest request) {
        return APIResponse.<ClassRoomResponse>builder()
                .result(classRoomService.updateClass(classId, request))
                .build();
    }

    // (Soft delete - change status)
    @DeleteMapping("/{classId}")
    APIResponse<String> deleteClass(
            @PathVariable String classId) {
        classRoomService.deleteClass(classId);
        return APIResponse.<String>builder()
                .result("Class has been cancelled successfully")
                .build();
    }

    @PostMapping("/{classId}/enroll")
    APIResponse<EnrollmentResponse> enrollClass(
            @PathVariable String classId) {
        return APIResponse.<EnrollmentResponse>builder()
                .result(enrollmentService.enrollClass(classId))
                .build();
    }

    @GetMapping("/{classId}/members")
    public APIResponse<List<EnrollmentResponse>> getClassMembers(
            @PathVariable String classId) {
        return APIResponse.<List<EnrollmentResponse>>builder()
                .result(enrollmentService.getClassMembers(classId))
                .build();
    }

    @GetMapping("/{classId}/enrollments/pending")
    public APIResponse<List<EnrollmentResponse>> getPendingEnrollments(
            @PathVariable String classId) {
        return APIResponse.<List<EnrollmentResponse>>builder()
                .result(enrollmentService.getPendingRequestsOfClass(classId))
                .build();
    }

    @GetMapping("/search")
    public APIResponse<List<TutorSearchResponse>> searchClasses(
            @RequestParam(required = false) String subjectName,
            @RequestParam(required = false) String topicName,
            @RequestParam(required = false) LearningFormat format,
            @RequestParam(required = false, name = "keyword") String userSearchKeyword) {
        return APIResponse.<List<TutorSearchResponse>>builder()
                .result(classRoomService.searchClassesGroupedByTutor(subjectName, topicName, format, userSearchKeyword))
                .build();
    }
}
