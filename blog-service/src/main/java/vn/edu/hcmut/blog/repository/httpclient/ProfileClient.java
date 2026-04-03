package vn.edu.hcmut.blog.repository.httpclient;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import vn.edu.hcmut.blog.dto.response.APIResponse;
import vn.edu.hcmut.blog.dto.response.ProfileResponse;

@FeignClient(name = "profile-service", url = "${app.services.profile}")
public interface ProfileClient {
    @GetMapping("/internal/users/{id}")
    APIResponse<ProfileResponse> findUserProfileById(@PathVariable String id);

    @PostMapping("/internal/users/{id}/points")
    void updatePoints(@PathVariable String id, @RequestParam Long delta);
}
