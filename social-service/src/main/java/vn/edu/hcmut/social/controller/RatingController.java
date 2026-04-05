package vn.edu.hcmut.social.controller;

import java.util.List;

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
import vn.edu.hcmut.social.dto.request.TutorReviewRequest;
import vn.edu.hcmut.social.dto.response.*;
import vn.edu.hcmut.social.exception.AppException;
import vn.edu.hcmut.social.exception.ErrorCode;
import vn.edu.hcmut.social.service.RatingService;
import vn.edu.hcmut.social.service.TutorReviewService;

@RestController
@RequestMapping("/ratings")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Rating", description = "Rating APIs for resources")
public class RatingController {
    RatingService ratingService;
    TutorReviewService tutorReviewService;

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

    @PostMapping("/resource")
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

    @GetMapping("/resource/{resourceId}/my-rating")
    public APIResponse<RatingResponse> getUserRatingForResource(@PathVariable String resourceId) {
        String userId = getProfileIdFromToken();
        return APIResponse.<RatingResponse>builder()
                .result(ratingService.getUserRatingForResource(resourceId, userId))
                .message("Get user rating successfully")
                .build();
    }

    @GetMapping("/ranking-stats")
    public APIResponse<RankingStatsResponse> getRankingStats() {
        return APIResponse.<RankingStatsResponse>builder()
                .result(ratingService.getRankingStats())
                .message("Get ranking stats successfully")
                .build();
    }

    @GetMapping("/internal/ranking-stats")
    public RankingStatsResponse getRankingStatsInternal() {
        return ratingService.getRankingStats();
    }

    @GetMapping("/internal/engagement-stats")
    public List<ResourceEngagementStatsResponse> getEngagementStats() {
        return ratingService.getEngagementStats();
    }

    // Tutor Review Endpoints

    @PostMapping("/tutor")
    public APIResponse<TutorReviewResponse> createTutorReview(@RequestBody @Valid TutorReviewRequest request) {
        String userId = getProfileIdFromToken();
        return APIResponse.<TutorReviewResponse>builder()
                .result(tutorReviewService.createReview(request, userId))
                .message("Review created successfully")
                .build();
    }

    @PutMapping("/tutor/{reviewId}")
    public APIResponse<TutorReviewResponse> updateTutorReview(
            @PathVariable String reviewId, @RequestBody @Valid TutorReviewRequest request) {
        String userId = getProfileIdFromToken();
        return APIResponse.<TutorReviewResponse>builder()
                .result(tutorReviewService.updateReview(reviewId, request, userId))
                .message("Review updated successfully")
                .build();
    }

    @DeleteMapping("/tutor/{reviewId}")
    public APIResponse<Void> deleteTutorReview(@PathVariable String reviewId) {
        String userId = getProfileIdFromToken();
        tutorReviewService.deleteReview(reviewId, userId);
        return APIResponse.<Void>builder()
                .message("Review deleted successfully")
                .build();
    }

    @GetMapping("/tutor/{tutorId}")
    public APIResponse<Page<TutorReviewResponse>> getTutorReviews(
            @PathVariable String tutorId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return APIResponse.<Page<TutorReviewResponse>>builder()
                .result(tutorReviewService.getReviewsByTutor(tutorId, pageable))
                .message("Get tutor reviews successfully")
                .build();
    }

    @GetMapping("/tutor/{tutorId}/summary")
    public APIResponse<TutorReviewSummaryResponse> getTutorReviewSummary(@PathVariable String tutorId) {
        return APIResponse.<TutorReviewSummaryResponse>builder()
                .result(tutorReviewService.getSummary(tutorId))
                .message("Get tutor review summary successfully")
                .build();
    }

    @GetMapping("/tutor/{tutorId}/user/{userId}")
    public APIResponse<TutorReviewResponse> getTutorReviewByUser(
            @PathVariable String tutorId, @PathVariable String userId) {
        return APIResponse.<TutorReviewResponse>builder()
                .result(tutorReviewService.getReviewByUserAndTutor(userId, tutorId))
                .message("Get tutor review by user successfully")
                .build();
    }
}
