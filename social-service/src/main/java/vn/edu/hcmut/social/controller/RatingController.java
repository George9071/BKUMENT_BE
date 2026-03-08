package vn.edu.hcmut.social.controller;

import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import vn.edu.hcmut.social.dto.request.RatingRequest;
import vn.edu.hcmut.social.dto.response.APIResponse;
import vn.edu.hcmut.social.dto.response.RatingResponse;
import vn.edu.hcmut.social.exception.AppException;
import vn.edu.hcmut.social.exception.ErrorCode;
import vn.edu.hcmut.social.service.RatingService;

@RestController
@RequestMapping("/ratings")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Rating", description = "Rating APIs for resources")
public class RatingController {
    RatingService ratingService;

    private String getProfileIdFromToken() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            Jwt jwt = jwtAuth.getToken();

            String profileId = jwt.getClaimAsString("profile_id");
            if (profileId == null || profileId.isBlank()) {
                throw new AppException(ErrorCode.INVALID_TOKEN_CLAIMS);
            }

            return profileId;
        }

        throw new AppException(ErrorCode.UNAUTHENTICATED);
    }

    @PostMapping
    public APIResponse<RatingResponse> createOrUpdateRating(@RequestBody @Valid RatingRequest request) {
        String userId = getProfileIdFromToken();
        return APIResponse.<RatingResponse>builder()
                .result(ratingService.createOrUpdateRating(request, userId))
                .message("Rating processed successfully")
                .build();
    }

    @GetMapping("/resource/{resourceId}")
    public APIResponse<Page<RatingResponse>> getRatingsByResource(
            @PathVariable String resourceId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return APIResponse.<Page<RatingResponse>>builder()
                .result(ratingService.getRatingsByResource(resourceId, pageable))
                .message("Get ratings successfully")
                .build();
    }

    @GetMapping("/resource/{resourceId}/average")
    public APIResponse<Double> getAverageRating(@PathVariable String resourceId) {
        return APIResponse.<Double>builder()
                .result(ratingService.getAverageRating(resourceId))
                .message("Get average rating successfully")
                .build();
    }
}
