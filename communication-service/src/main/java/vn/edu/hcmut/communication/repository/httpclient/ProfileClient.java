package vn.edu.hcmut.communication.repository.httpclient;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import vn.edu.hcmut.communication.dto.response.APIResponse;
import vn.edu.hcmut.communication.dto.response.ProfileResponse;

@FeignClient(name = "profile-service", url = "${app.services.profile.url}")
public interface ProfileClient {
    @GetMapping(value = "/internal/users/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    APIResponse<ProfileResponse> getProfile(@PathVariable("id") String id);
}
