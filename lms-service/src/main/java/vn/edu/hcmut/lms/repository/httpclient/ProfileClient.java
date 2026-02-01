package vn.edu.hcmut.lms.repository.httpclient;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import vn.edu.hcmut.lms.dto.response.ProfileResponse;
import vn.edu.hcmut.lms.dto.sync.SubjectSyncRequest;
import vn.edu.hcmut.lms.dto.sync.TopicSyncRequest;

import java.util.List;
import java.util.Set;

@FeignClient(name = "profile-service", url = "${app.services.profile}")
public interface ProfileClient {
    @GetMapping(value = "/internal/users/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    ProfileResponse getProfile(@PathVariable("id") String id);

    @PutMapping("/internal/users/{id}/tutor-register")
    void addRole(@PathVariable("id") String id, @RequestBody String role);

    @PostMapping("/internal/users/batch")
    List<ProfileResponse> getProfiles(@RequestBody List<String> profileIds);

    @PutMapping("/internal/users/{id}/subjects")
    void updateTutorSubjects(@PathVariable("id") String id, @RequestBody Set<String> subjectIds);

    @PostMapping("/internal/metadata/subjects")
    void syncSubjects(@RequestBody List<SubjectSyncRequest> subjects);

    @PostMapping("/internal/metadata/topics")
    void syncTopics(@RequestBody List<TopicSyncRequest> topics);
}
