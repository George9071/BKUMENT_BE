package vn.edu.hcmut.social.controller;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import vn.edu.hcmut.social.dto.request.RatingRequest;
import vn.edu.hcmut.social.dto.request.ClassReviewRequest;
import vn.edu.hcmut.social.dto.request.ClassReviewUpdateRequest;
import vn.edu.hcmut.social.dto.response.*;
import vn.edu.hcmut.social.service.RatingService;
import vn.edu.hcmut.social.service.ClassReviewService;
import vn.edu.hcmut.social.utils.SecurityUtils;

@RestController
@RequestMapping("/ratings")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Rating", description = "Rating APIs for resources")
public class RatingController {
    RatingService ratingService;
    ClassReviewService classReviewService;
    SecurityUtils securityUtils;

    @PostMapping("/resource")
    public APIResponse<RatingResponse> createOrUpdateRating(@RequestBody @Valid RatingRequest request) {
        String userId = securityUtils.getProfileId();
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
        String userId = securityUtils.getProfileId();
        return APIResponse.<RatingResponse>builder()
                .result(ratingService.getUserRatingForResource(resourceId, userId).orElse(null))
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

    // Class Review Endpoints

    @PostMapping("/classes")
    public APIResponse<ClassReviewResponse> createClassReview(@RequestBody @Valid ClassReviewRequest request) {
        String userId = securityUtils.getProfileId();
        return APIResponse.<ClassReviewResponse>builder()
                .result(classReviewService.createReview(request, userId))
                .message("Review created successfully")
                .build();
    }

    @PutMapping("/classes/{reviewId}")
    public APIResponse<ClassReviewResponse> updateClassReview(
            @PathVariable String reviewId, @RequestBody @Valid ClassReviewUpdateRequest request) {
        String userId = securityUtils.getProfileId();
        return APIResponse.<ClassReviewResponse>builder()
                .result(classReviewService.updateReview(reviewId, request, userId))
                .message("Review updated successfully")
                .build();
    }

    @DeleteMapping("/classes/{reviewId}")
    public APIResponse<Void> deleteClassReview(@PathVariable String reviewId) {
        String userId = securityUtils.getProfileId();
        classReviewService.deleteReview(reviewId, userId);
        return APIResponse.<Void>builder()
                .message("Review deleted successfully")
                .build();
    }

    @GetMapping("/classes/{classId}")
    public APIResponse<Page<ClassReviewResponse>> getClassReviews(
            @PathVariable String classId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return APIResponse.<Page<ClassReviewResponse>>builder()
                .result(classReviewService.getReviewsByClass(classId, pageable))
                .message("Get class reviews successfully")
                .build();
    }

    @GetMapping("/classes/{classId}/summary")
    public APIResponse<ClassReviewSummaryResponse> getClassReviewSummary(@PathVariable String classId) {
        return APIResponse.<ClassReviewSummaryResponse>builder()
                .result(classReviewService.getSummary(classId))
                .message("Get class review summary successfully")
                .build();
    }

    @GetMapping("/classes/{classId}/user/{userId}")
    public APIResponse<ClassReviewResponse> getClassReviewByUser(
            @PathVariable String classId, @PathVariable String userId) {
        return APIResponse.<ClassReviewResponse>builder()
                .result(classReviewService.getReviewByUserAndClass(userId, classId))
                .message("Get class review by user successfully")
                .build();
    }
}
