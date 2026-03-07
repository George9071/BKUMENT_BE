package vn.edu.hcmut.lms.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.*;
import vn.edu.hcmut.lms.constant.EnrollmentStatus;
import vn.edu.hcmut.lms.constant.LearningFormat;
import vn.edu.hcmut.lms.dto.request.ClassRoomCreationRequest;
import vn.edu.hcmut.lms.dto.request.ClassRoomUpdateRequest;
import vn.edu.hcmut.lms.dto.response.*;
import vn.edu.hcmut.lms.service.ClassRoomService;
import vn.edu.hcmut.lms.service.EnrollmentService;

import java.util.List;

@RestController
@RequestMapping("/classes")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Classroom management", description = "APIs for managing classes, searching, and student enrollments")
public class ClassRoomController {
    ClassRoomService classRoomService;
    EnrollmentService enrollmentService;

    // ================================== //
    //              TUTOR APIs
    // ================================== //

    @Operation(summary = "Create a new class",
            description = "Tutor creates a new class. Status defaults to ENROLLING.")
    @PostMapping
    APIResponse<ClassRoomResponse> createClass(
            @RequestBody @Valid ClassRoomCreationRequest request) {
        return APIResponse.<ClassRoomResponse>builder()
                .result(classRoomService.createClass(request))
                .build();
    }

    @Operation(summary = "Get classes I am teaching",
            description = "Retrieves a paginated list of classes managed by the current authenticated tutor.")
    @GetMapping("/teaching")
    public APIResponse<PageResponse<ClassRoomResponse>> getMyClassesAsTutor(
            @Parameter(description = "Page number (1-based index)")
            @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "Number of records per page")
            @RequestParam(defaultValue = "10") int size) {
        return APIResponse.<PageResponse<ClassRoomResponse>>builder()
                .result(classRoomService.getMyClassesAsTutor(page, size))
                .build();
    }


    @Operation(summary = "Update class details",
            description = "Updates an existing class. Only the owning tutor can perform this action.")
    @PutMapping("/{classId}")
    APIResponse<ClassRoomResponse> updateClass(
            @PathVariable String classId,
            @RequestBody @Valid ClassRoomUpdateRequest request) {
        return APIResponse.<ClassRoomResponse>builder()
                .result(classRoomService.updateClass(classId, request))
                .build();
    }

    @Operation(summary = "Cancel a class",
            description = "Soft deletes a class by changing its status to CANCELLED.")
    @DeleteMapping("/{classId}")
    APIResponse<String> deleteClass(
            @PathVariable String classId) {
        classRoomService.deleteClass(classId);
        return APIResponse.<String>builder()
                .result("Class has been cancelled successfully")
                .build();
    }

    @Operation(summary = "Get pending enrollments for a class",
            description = "Tutor views students waiting for approval to join the class.")
    @GetMapping("/{classId}/enrollments/pending")
    public APIResponse<List<EnrollmentResponse>> getPendingEnrollments(
            @PathVariable String classId) {
        return APIResponse.<List<EnrollmentResponse>>builder()
                .result(enrollmentService.getPendingRequestsOfClass(classId))
                .build();
    }

    @Operation(summary = "Get class members",
            description = "Retrieves a list of students who are currently approved members of the class.")
    @GetMapping("/{classId}/members")
    public APIResponse<List<EnrollmentResponse>> getClassMembers(
            @PathVariable String classId) {
        return APIResponse.<List<EnrollmentResponse>>builder()
                .result(enrollmentService.getClassMembers(classId))
                .build();
    }

    // ================================== //
    //              STUDENT APIs
    // ================================== //

    @Operation(summary = "Enroll in a class",
            description = "Student requests to join a class. Status defaults to PENDING.")
    @PostMapping("/{classId}/enroll")
    APIResponse<EnrollmentResponse> enrollClass(
            @PathVariable String classId) {
        return APIResponse.<EnrollmentResponse>builder()
                .result(enrollmentService.enrollClass(classId))
                .build();
    }

    @Operation(summary = "Get classes I am studying",
            description = "Retrieves a paginated list of classes the student has successfully joined.")
    @GetMapping("/my-class")
    public APIResponse<PageResponse<ClassRoomResponse>> getMyClassesAsUser(
            @Parameter(description = "Page number") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "10") int size) {
        return APIResponse.<PageResponse<ClassRoomResponse>>builder()
                .result(enrollmentService.getMyClassesByEnrollmentStatus(EnrollmentStatus.APPROVED, page, size))
                .build();
    }

    @Operation(summary = "Get my pending class requests",
            description = "Retrieves a paginated list of classes the student is waiting to be approved.")
    @GetMapping("/pending")
    public APIResponse<PageResponse<ClassRoomResponse>> getMyPendingClasses(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return APIResponse.<PageResponse<ClassRoomResponse>>builder()
                .result(enrollmentService.getMyClassesByEnrollmentStatus(EnrollmentStatus.PENDING, page, size))
                .build();
    }

    // ================================== //
    //              PUBLIC APIs
    // ================================== //
    @Operation(summary = "Search available classes",
            description = "Searches classes by subject, topic, format, and keyword, grouped by Tutor.")
    @GetMapping("/search")
    public APIResponse<PageResponse<TutorSearchResponse>> searchClasses(
            @Parameter(description = "Filter by subject name")
            @RequestParam(required = false) String subjectName,
            @Parameter(description = "Filter by topic name")
            @RequestParam(required = false) String topicName,
            @Parameter(description = "Filter by learning format")
            @RequestParam(required = false) LearningFormat format,
            @Parameter(description = "Search keyword for class name or description")
            @RequestParam(required = false, name = "keyword") String userSearchKeyword,
            @Parameter(description = "Page number") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "10") int size) {
        return APIResponse.<PageResponse<TutorSearchResponse>>builder()
                .result(classRoomService.searchClassesGroupedByTutor(
                        subjectName, topicName, format, userSearchKeyword,
                        page, size))
                .build();
    }

    @Operation(summary = "Get classes of a specific tutor",
            description = "Retrieves a paginated list of public classes taught by a specific tutor ID.")
    @GetMapping("/tutors/{tutorId}")
    public APIResponse<PageResponse<ClassRoomResponse>> getClassesOfTutor(
            @PathVariable String tutorId,
            @Parameter(description = "Page number") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "10") int size) {
        return APIResponse.<PageResponse<ClassRoomResponse>>builder()
                .result(classRoomService.getClassesOfTutor(tutorId, page, size))
                .build();
    }
}
