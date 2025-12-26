package vn.edu.hcmut.profile.controller;

import org.springframework.web.bind.annotation.*;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import vn.edu.hcmut.profile.dto.response.ProfileResponse;
import vn.edu.hcmut.profile.service.ProfileService;

@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ProfileController {
    ProfileService profileService;

    @GetMapping("/{profileId}")
    ProfileResponse getProfile(@PathVariable String profileId) {
        return profileService.getProfile(profileId);
    }

    @GetMapping("/my-profile")
    public ProfileResponse getMyProfile() {
        return profileService.getMyProfile();
    }
}
