package vn.edu.hcmut.profile.controller;

import java.util.List;
import java.util.Set;

import org.springframework.web.bind.annotation.*;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import vn.edu.hcmut.profile.dto.request.ProfileCreationRequest;
import vn.edu.hcmut.profile.dto.response.ProfileResponse;
import vn.edu.hcmut.profile.service.ProfileNeo4jService;
import vn.edu.hcmut.profile.service.ProfileService;

@RestController
@RequestMapping("/internal/users")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class InternalProfileController {
    ProfileService profileService;
    ProfileNeo4jService profileNeo4jService;

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

    @PostMapping("/verify-email/{accountId}")
    void verifyEmail(@PathVariable String accountId) {
        profileService.verifyEmail(accountId);
    }

    @GetMapping("/email")
    ProfileResponse getProfileByEmail(@RequestParam("email") String email) {
        return profileService.getProfileByEmail(email);
    }

    @PostMapping("/batch")
    List<ProfileResponse> getProfiles(@RequestBody List<String> profileIds) {
        return profileService.getProfilesByIds(profileIds);
    }

    @PutMapping("/{profileId}/roles/{roleName}")
    void addRole(@PathVariable("profileId") String profileId, @PathVariable("roleName") String role) {
        profileNeo4jService.addRole(profileId, role);
    }

    @DeleteMapping("/{profileId}/roles/{roleName}")
    void removeRole(@PathVariable("profileId") String profileId, @PathVariable("roleName") String role) {
        profileNeo4jService.removeRole(profileId, role);
    }

    @PutMapping("/{profileId}/subjects")
    void updateTutorSubjects(@PathVariable("profileId") String profileId, @RequestBody Set<String> subjectIds) {
        profileNeo4jService.updateTutorSubjects(profileId, subjectIds);
    }

    @DeleteMapping("/{profileId}")
    void deleteProfile(@PathVariable String profileId) {
        profileService.deleteProfile(profileId);
    }

    @PostMapping("/{profileId}/points")
    public void updatePoints(@PathVariable String profileId, @RequestParam Long delta) {
        profileService.updatePoints(profileId, delta);
    }
}
