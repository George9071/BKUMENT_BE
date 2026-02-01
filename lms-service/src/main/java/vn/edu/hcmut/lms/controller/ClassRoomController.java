package vn.edu.hcmut.lms.controller;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.*;
import vn.edu.hcmut.lms.dto.request.ClassRoomCreationRequest;
import vn.edu.hcmut.lms.dto.request.ClassRoomUpdateRequest;
import vn.edu.hcmut.lms.dto.response.APIResponse;
import vn.edu.hcmut.lms.dto.response.ClassRoomResponse;
import vn.edu.hcmut.lms.service.ClassRoomService;

import java.util.List;

@RestController
@RequestMapping("/classes")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ClassRoomController {
    ClassRoomService classRoomService;

    @PostMapping
    APIResponse<ClassRoomResponse> createClass(@RequestBody ClassRoomCreationRequest request) {
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

    @PutMapping("/{classId}")
    APIResponse<ClassRoomResponse> updateClass(@PathVariable String classId, @RequestBody ClassRoomUpdateRequest request) {
        return APIResponse.<ClassRoomResponse>builder()
                .result(classRoomService.updateClass(classId, request))
                .build();
    }

    // Hủy lớp (Soft delete)
    @DeleteMapping("/{classId}")
    APIResponse<String> deleteClass(@PathVariable String classId) {
        classRoomService.deleteClass(classId);
        return APIResponse.<String>builder()
                .result("Class has been cancelled successfully")
                .build();
    }
}
