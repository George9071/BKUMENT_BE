package vn.edu.hcmut.profile.controller;

import java.util.List;
import java.util.Set;

import org.springframework.web.bind.annotation.*;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import vn.edu.hcmut.profile.dto.request.ProfileCreationRequest;
import vn.edu.hcmut.profile.dto.response.APIResponse;
import vn.edu.hcmut.profile.dto.response.ProfileResponse;
import vn.edu.hcmut.profile.service.ProfileService;

@RestController
@RequestMapping("/internal/users")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class InternalProfileController {
    ProfileService profileService;

    @PostMapping
    APIResponse<ProfileResponse> createProfile(@RequestBody ProfileCreationRequest request) {
        return APIResponse.<ProfileResponse>builder()
                .result(profileService.createProfile(request))
                .build();
    }

    @GetMapping("/{id}")
    APIResponse<ProfileResponse> getProfile(@PathVariable String id) {
        return APIResponse.<ProfileResponse>builder()
                .result(profileService.getProfile(id))
                .build();
    }

    @GetMapping("/account/{accountId}")
    APIResponse<ProfileResponse> getProfileByAccountId(@PathVariable String accountId) {
        return APIResponse.<ProfileResponse>builder()
                .result(profileService.getProfileByAccountId(accountId))
                .build();
    }

    @PutMapping("/{id}/tutor-register")
    void addRole(@PathVariable String id, @RequestParam String role) {
        profileService.addRole(id, role);
    }

    @PostMapping("/batch")
    APIResponse<List<ProfileResponse>> getProfiles(@RequestBody List<String> profileIds) {
        return APIResponse.<List<ProfileResponse>>builder()
                .result(profileService.getProfilesByIds(profileIds))
                .build();
    }

    @PutMapping("/{id}/subjects")
    public void updateTutorSubjects(@PathVariable String id, @RequestBody Set<String> subjectIds) {
        profileService.updateTutorSubjects(id, subjectIds);
    }

    @DeleteMapping("/{profileId}")
    public void deleteProfile(@PathVariable String profileId) {
        profileService.deleteProfile(profileId);
    }
}
