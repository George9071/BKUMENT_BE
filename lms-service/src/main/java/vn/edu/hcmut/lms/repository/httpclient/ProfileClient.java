package vn.edu.hcmut.lms.repository.httpclient;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import vn.edu.hcmut.lms.dto.response.APIResponse;
import vn.edu.hcmut.lms.dto.response.ProfileResponse;
import vn.edu.hcmut.lms.dto.sync.*;

import java.util.List;
import java.util.Set;

@FeignClient(name = "profile-service", url = "${app.services.profile}")
public interface ProfileClient {
    @GetMapping(value = "/internal/users/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    APIResponse<ProfileResponse> getProfile(@PathVariable("id") String id);

    @PutMapping("/internal/users/{profileId}/roles/{roleName}")
    void addRole(@PathVariable("profileId") String id, @PathVariable("roleName") String role);

    @DeleteMapping("/internal/users/{profileId}/roles/{roleName}")
    void removeRole(@PathVariable("profileId") String profileId, @PathVariable("roleName") String role);

    @PostMapping("/internal/users/batch")
    APIResponse<List<ProfileResponse>> getProfiles(@RequestBody List<String> profileIds);

    @PutMapping("/internal/users/{id}/subjects")
    void updateTutorSubjects(@PathVariable("id") String id, @RequestBody Set<String> subjectIds);

    @PostMapping("/internal/metadata/subjects")
    void syncSubjects(@RequestBody List<SubjectSyncRequest> subjects);

    @PostMapping("/internal/metadata/topics")
    void syncTopics(@RequestBody List<TopicSyncRequest> topics);

    @PostMapping("/internal/metadata/tutor-subjects")
    void syncTutorSubjects(@RequestBody List<TutorSubjectSyncRequest> requests);

    @PostMapping("/internal/metadata/classrooms")
    void syncClassRoom(@RequestBody ClassRoomSyncRequest request);

    @PostMapping("/internal/metadata/classrooms/batch")
    void syncClasses(@RequestBody List<ClassRoomSyncRequest> requests);

    @PostMapping("/internal/metadata/enrollments/batch")
    void syncAllEnrollments(@RequestBody List<EnrollmentSyncRequest> requests);

    @PostMapping("/internal/metadata/enrollments")
    void addEnrollment(@RequestBody EnrollmentSyncRequest request);

    @DeleteMapping("/internal/metadata/enrollments/students/{studentId}/classes/{classId}")
    void removeEnrollment(
            @PathVariable("studentId") String studentId,
            @PathVariable("classId") String classId);

    @DeleteMapping("/internal/metadata/classrooms/{classId}")
    void deleteClassRoom(@PathVariable("classId") String classId);
}
