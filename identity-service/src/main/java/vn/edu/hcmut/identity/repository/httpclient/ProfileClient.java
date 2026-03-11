package vn.edu.hcmut.identity.repository.httpclient;

import jakarta.validation.Valid;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import vn.edu.hcmut.identity.dto.request.ProfileCreationRequest;
import vn.edu.hcmut.identity.dto.response.APIResponse;
import vn.edu.hcmut.identity.dto.response.ProfileResponse;

@FeignClient(name = "profile-service", url = "${app.services.profile}")
public interface ProfileClient {
    @PostMapping(value = "/internal/users", produces = MediaType.APPLICATION_JSON_VALUE)
    APIResponse<ProfileResponse> createProfile(@RequestBody @Valid ProfileCreationRequest request);

    @GetMapping("/internal/users/account/{accountId}")
    APIResponse<ProfileResponse> getProfileByAccountId(@PathVariable("accountId") String accountId);

    @DeleteMapping("/internal/users/{userId}")
    void deleteProfile(@PathVariable("userId") String userId);
}
