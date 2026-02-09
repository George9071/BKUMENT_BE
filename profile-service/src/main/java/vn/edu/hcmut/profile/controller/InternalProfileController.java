package vn.edu.hcmut.profile.controller;

import java.util.List;
import java.util.Set;

import org.springframework.web.bind.annotation.*;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import vn.edu.hcmut.profile.dto.request.ProfileCreationRequest;
import vn.edu.hcmut.profile.dto.response.ProfileResponse;
import vn.edu.hcmut.profile.service.ProfileService;

@RestController
@RequestMapping("/internal/users")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class InternalProfileController {
    ProfileService profileService;

    @PostMapping
    ProfileResponse createProfile(@RequestBody ProfileCreationRequest request) {
        return profileService.createProfile(request);
    }

    @GetMapping("/{id}")
    ProfileResponse getProfile(@PathVariable String id) {
        return profileService.getProfile(id);
    }

    @GetMapping("/account/{accountId}")
    ProfileResponse getProfileByAccountId(@PathVariable String accountId) {
        return profileService.getProfileByAccountId(accountId);
    }

    @PutMapping("/{id}/tutor-register")
    void addRole(@PathVariable String id, @RequestBody String role) {
        profileService.addRole(id, role);
    }

    @PostMapping("/batch")
    List<ProfileResponse> getProfiles(@RequestBody List<String> profileIds) {
        return profileService.getProfilesByIds(profileIds);
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
