package vn.edu.hcmut.profile.controller;

import java.util.List;

import org.springframework.web.bind.annotation.*;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import vn.edu.hcmut.profile.dto.response.APIResponse;
import vn.edu.hcmut.profile.dto.response.ProfileResponse;
import vn.edu.hcmut.profile.service.ProfileService;

@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ProfileController {
    ProfileService profileService;

    @GetMapping("/{profileId}")
    APIResponse<ProfileResponse> getProfile(@PathVariable String profileId) {
        return APIResponse.<ProfileResponse>builder()
                .result(profileService.getProfile(profileId))
                .build();
    }

    @GetMapping("/my-profile")
    APIResponse<ProfileResponse> getMyProfile() {
        return APIResponse.<ProfileResponse>builder()
                .result(profileService.getMyProfile())
                .build();
    }

    @GetMapping("/all")
    APIResponse<List<ProfileResponse>> getAllProfiles() {
        return APIResponse.<List<ProfileResponse>>builder()
                .result(profileService.getAllProfiles())
                .build();
    }
}
