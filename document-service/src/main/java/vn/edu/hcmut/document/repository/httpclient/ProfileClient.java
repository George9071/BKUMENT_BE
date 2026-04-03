package vn.edu.hcmut.document.repository.httpclient;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import vn.edu.hcmut.document.dto.response.APIResponse;
import vn.edu.hcmut.document.dto.response.ProfilePageResponse;
import vn.edu.hcmut.document.dto.response.ProfileResponse;
import vn.edu.hcmut.document.dto.response.UniversityResponse;

@FeignClient(name = "profile-service", url = "${app.services.profile}")
public interface ProfileClient {

    @GetMapping("/internal/universities/search")
    ProfilePageResponse<UniversityResponse> searchUniversities(@RequestParam(value = "q", required = false) String q);

    @GetMapping("/internal/users/{id}")
    APIResponse<ProfileResponse> findUserProfileById(@PathVariable String id);

    @PostMapping("/internal/users/{id}/points")
    void updatePoints(@PathVariable String id, @RequestParam Long delta);
}
