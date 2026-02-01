package vn.edu.hcmut.profile.controller;

import java.util.List;

import org.springframework.web.bind.annotation.*;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import vn.edu.hcmut.profile.dto.request.ProfileUpdateRequest;
import vn.edu.hcmut.profile.dto.response.APIResponse;
import vn.edu.hcmut.profile.dto.response.ProfileResponse;
import vn.edu.hcmut.profile.service.ProfileService;

@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ProfileController {
    ProfileService profileService;

    @GetMapping("/my-profile")
    APIResponse<ProfileResponse> getMyProfile() {
        return APIResponse.<ProfileResponse>builder()
                .result(profileService.getMyProfile())
                .build();
    }

    @PatchMapping("/update")
    APIResponse<ProfileResponse> updateMyProfile(@RequestBody ProfileUpdateRequest request) {
        return APIResponse.<ProfileResponse>builder()
                .result(profileService.updateProfile(request))
                .build();
    }

    @GetMapping("/{profileId}")
    APIResponse<ProfileResponse> getProfile(@PathVariable String profileId) {
        return APIResponse.<ProfileResponse>builder()
                .result(profileService.getProfile(profileId))
                .build();
    }
}
