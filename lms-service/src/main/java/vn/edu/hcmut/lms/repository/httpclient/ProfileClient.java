package vn.edu.hcmut.lms.repository.httpclient;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import vn.edu.hcmut.lms.dto.response.ProfileResponse;
import vn.edu.hcmut.lms.dto.sync.*;

import java.util.List;
import java.util.Set;

@FeignClient(name = "profile-service", url = "${app.services.profile}")
public interface ProfileClient {
    @GetMapping(value = "/internal/users/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    ProfileResponse getProfile(@PathVariable String id);

    @PutMapping("/internal/users/{profileId}/roles/{roleName}")
    void addRole(@PathVariable("profileId") String profileId, @PathVariable("roleName") String role);

    @DeleteMapping("/internal/users/{profileId}/roles/{roleName}")
    void removeRole(@PathVariable("profileId") String profileId, @PathVariable("roleName") String role);

    @PostMapping("/internal/users/batch")
    List<ProfileResponse> getProfiles(@RequestBody List<String> profileIds);

    @PutMapping("/internal/users/{profileId}/subjects")
    void updateTutorSubjects(@PathVariable("profileId") String profileId, @RequestBody Set<String> subjectIds);

    @PostMapping("/internal/metadata/subjects")
    void syncSubjects(@RequestBody List<SubjectSyncRequest> subjects);

    @PostMapping("/internal/metadata/topics")
    void syncTopics(@RequestBody List<TopicSyncRequest> topics);
}
