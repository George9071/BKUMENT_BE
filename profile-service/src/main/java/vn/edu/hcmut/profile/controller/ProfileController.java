package vn.edu.hcmut.profile.controller;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
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
import vn.edu.hcmut.profile.service.FollowService;
import vn.edu.hcmut.profile.service.ProfileService;
import vn.edu.hcmut.profile.service.RecommendationService;

@RestController
@RequiredArgsConstructor
@Validated
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Profile management", description = "APIs for managing user profiles, searching, and graph relationships")
public class ProfileController {
    ProfileService profileService;
    FollowService followService;
    RecommendationService recommendationService;

    @GetMapping("/search")
    public APIResponse<PageResponse<ProfileResponse>> searchUsers(
            @RequestParam("keyword") String keyword,
            @Min(value = 1, message = "Page number must be greater than 0")
            @RequestParam(defaultValue = "1") int page,
            @Min(value = 1, message = "Page size must be greater than 0")
            @RequestParam(defaultValue = "10") int size) {

        return APIResponse.<PageResponse<ProfileResponse>>builder()
                .result(profileService.searchProfile(keyword, page, size))
                .build();
    }

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
    APIResponse<ProfileResponse> updateMyProfile(@Valid @RequestBody ProfileUpdateRequest request) {
        return APIResponse.<ProfileResponse>builder()
                .result(profileService.updateProfile(request))
                .build();
    }

    @Operation(summary = "Get profile by ID", description = "Retrieves public profile information of a specific user.")
    @GetMapping("/{profileId}")
    APIResponse<ProfileResponse> getProfile(
            @Parameter(description = "ID of the profile to retrieve") @PathVariable String profileId) {
        return APIResponse.<ProfileResponse>builder()
                .result(profileService.getProfile(profileId))
                .build();
    }

    @Operation(summary = "Follow a user", description = "Current authenticated user follows the specified profile.")
    @PostMapping("/{profileId}/follow")
    public APIResponse<String> followProfile(
            @Parameter(description = "ID of the profile to follow") @PathVariable("profileId") String followeeId) {
        String followerId = profileService.getMyProfile().getId();
        followService.followProfile(followerId, followeeId);

        return APIResponse.<String>builder()
                .result("Successfully followed the user")
                .build();
    }

    @Operation(summary = "Unfollow a user", description = "Current authenticated user unfollows the specified profile.")
    @DeleteMapping("/{profileId}/follow")
    public APIResponse<String> unfollowProfile(
            @Parameter(description = "ID of the profile to unfollow") @PathVariable("profileId") String followeeId) {

        String followerId = profileService.getMyProfile().getId();
        followService.unfollowProfile(followerId, followeeId);

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
                .result(followService.getFollowers(profileId, page, size))
                .build();
    }

    @Operation(summary = "Get following", description = "Get a paginated list of users that this profile is following.")
    @GetMapping("/{profileId}/following")
    public APIResponse<PageResponse<ProfileResponse>> getFollowing(
            @PathVariable String profileId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {

        return APIResponse.<PageResponse<ProfileResponse>>builder()
                .result(followService.getFollowing(profileId, page, size))
                .build();
    }

    @Operation(
            summary = "Update my interests",
            description = "Replaces the current list of interested topics with a new one.")
    @PutMapping("/me/interests")
    public APIResponse<String> updateInterests(@RequestBody List<String> topicIds) {
        profileService.updateMyInterests(topicIds);
        return APIResponse.<String>builder()
                .result("Interests updated successfully")
                .build();
    }

    @GetMapping("/mayKnow")
    public APIResponse<PageResponse<ProfileResponse>> getPeopleYouMayKnow(
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "10") int size) {

        String profileId = profileService.getMyProfile().getId();
        return APIResponse.<PageResponse<ProfileResponse>>builder()
                .result(recommendationService.getPeopleYouMayKnow(profileId, page, size))
                .build();
    }
}
