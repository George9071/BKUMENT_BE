package vn.edu.hcmut.profile.controller;

import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import vn.edu.hcmut.profile.dto.request.ProfileUpdateRequest;
import vn.edu.hcmut.profile.dto.response.APIResponse;
import vn.edu.hcmut.profile.dto.response.PageResponse;
import vn.edu.hcmut.profile.dto.response.ProfileResponse;
import vn.edu.hcmut.profile.service.ProfileService;

@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Profile management", description = "APIs for managing user profiles, searching, and graph relationships")
public class ProfileController {
    ProfileService profileService;

    // ========================= //
    // MANAGE YOUR OWN PROFILE
    // ========================= //
    @Operation(
            summary = "Get my profile",
            description = "Retrieves the profile of the currently authenticated user based on JWT token.")
    @GetMapping("/my-profile")
    APIResponse<ProfileResponse> getMyProfile() {
        return APIResponse.<ProfileResponse>builder()
                .result(profileService.getMyProfile())
                .build();
    }

    @Operation(
            summary = "Update my profile",
            description = "Updates personal information of the currently authenticated user.")
    @PatchMapping("/my-profile")
    APIResponse<ProfileResponse> updateMyProfile(@RequestBody ProfileUpdateRequest request) {
        return APIResponse.<ProfileResponse>builder()
                .result(profileService.updateProfile(request))
                .build();
    }

    // ========================= //
    // PUBLIC PROFILE
    // ========================= //
    @Operation(summary = "Get profile by ID", description = "Retrieves public profile information of a specific user.")
    @GetMapping("/{profileId}")
    APIResponse<ProfileResponse> getProfile(
            @Parameter(description = "ID of the profile to retrieve") @PathVariable String profileId) {
        return APIResponse.<ProfileResponse>builder()
                .result(profileService.getProfile(profileId))
                .build();
    }

    // ========================= //
    // GRAPH RELATIONSHIPS
    // ========================= //
    @Operation(summary = "Follow a user", description = "Current authenticated user follows the specified profile.")
    @PostMapping("/{profileId}/follow")
    public APIResponse<String> followProfile(
            @Parameter(description = "ID of the profile to follow") @PathVariable("profileId") String followeeId) {

        String followerId = profileService.getMyProfile().getId();
        profileService.followProfile(followerId, followeeId);

        return APIResponse.<String>builder()
                .result("Successfully followed the user")
                .build();
    }

    @Operation(summary = "Unfollow a user", description = "Current authenticated user unfollows the specified profile.")
    @DeleteMapping("/{profileId}/follow")
    public APIResponse<String> unfollowProfile(
            @Parameter(description = "ID of the profile to unfollow") @PathVariable("profileId") String followeeId) {

        String followerId = profileService.getMyProfile().getId();
        profileService.unfollowProfile(followerId, followeeId);

        return APIResponse.<String>builder()
                .result("Successfully unfollowed the user")
                .build();
    }

    @Operation(summary = "Get followers", description = "Get a paginated list of users who are following this profile.")
    @GetMapping("/{profileId}/followers")
    public APIResponse<PageResponse<ProfileResponse>> getFollowers(
            @PathVariable String profileId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {

        return APIResponse.<PageResponse<ProfileResponse>>builder()
                .result(profileService.getFollowers(profileId, page, size))
                .build();
    }

    @Operation(summary = "Get following", description = "Get a paginated list of users that this profile is following.")
    @GetMapping("/{profileId}/following")
    public APIResponse<PageResponse<ProfileResponse>> getFollowing(
            @PathVariable String profileId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {

        return APIResponse.<PageResponse<ProfileResponse>>builder()
                .result(profileService.getFollowing(profileId, page, size))
                .build();
    }

    @GetMapping("/mayKnow")
    public APIResponse<PageResponse<ProfileResponse>> getPeopleYouMayKnow(
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "10") int size) {

        String profileId = profileService.getMyProfile().getId();
        return APIResponse.<PageResponse<ProfileResponse>>builder()
                .result(profileService.getPeopleYouMayKnow(profileId, page, size))
                .build();
    }
}
