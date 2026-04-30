package vn.edu.hcmut.social.repository.httpclient;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import vn.edu.hcmut.social.dto.response.ProfileResponse;

import java.util.List;

@FeignClient(name = "profile-service", url = "${app.services.profile}")
public interface ProfileClient {
    @GetMapping("/internal/users/{id}")
    ProfileResponse findUserProfileById(@PathVariable String id);

    @PostMapping("/internal/users/{id}/points")
    void updatePoints(@PathVariable String id, @RequestParam Long delta);

    @PostMapping("/internal/users/batch")
    List<ProfileResponse> getProfiles(@RequestBody List<String> profileIds);
}
