package vn.edu.hcmut.identity.repository.httpclient;

import jakarta.validation.Valid;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import vn.edu.hcmut.identity.dto.request.ProfileCreationRequest;
import vn.edu.hcmut.identity.dto.response.ProfileResponse;

@FeignClient(name = "profile-service", url = "${app.services.profile}")
public interface ProfileClient {
    @PostMapping(value = "/internal/users", produces = MediaType.APPLICATION_JSON_VALUE)
    ProfileResponse createProfile(@RequestBody @Valid ProfileCreationRequest request);

    @GetMapping("/internal/users/account/{accountId}")
    ProfileResponse getProfileByAccountId(@PathVariable("accountId") String accountId);

    @PostMapping("/internal/users/verify-email/{accountId}")
    void verifyEmail(@PathVariable String accountId);

    @GetMapping("/internal/users/email")
    ProfileResponse getProfileByEmail(@RequestParam("email") String email);
}
