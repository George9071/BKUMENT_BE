package vn.edu.hcmut.social.service;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import vn.edu.hcmut.social.dto.request.RatingRequest;
import vn.edu.hcmut.social.dto.response.RankingStatsResponse;
import vn.edu.hcmut.social.dto.response.RatingResponse;
import vn.edu.hcmut.social.dto.response.ResourceRatingStatsResponse;
import vn.edu.hcmut.social.entity.Rating;
import vn.edu.hcmut.social.repository.RatingRepository;
import vn.edu.hcmut.social.repository.httpclient.BlogClient;
import vn.edu.hcmut.social.repository.httpclient.DocumentClient;
import vn.edu.hcmut.social.repository.httpclient.ProfileClient;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class RatingService {
    RatingRepository ratingRepository;
    ProfileClient profileClient;
    DocumentClient documentClient;
    BlogClient blogClient;

    private Long getPointsForScore(Double score) {
        if (score == null) return 0L;
        if (score >= 4.5) return 2L;
        if (score >= 3.5) return 1L;
        if (score >= 2.5) return 0L;
        if (score >= 1.5) return -1L;
        return -2L;
    }

    private String getOwnerId(String resourceId) {
        try {
            return documentClient.getOwnerId(resourceId);
        } catch (Exception e1) {
            try {
                return blogClient.getOwnerId(resourceId);
            } catch (Exception e2) {
                log.warn(
                        "Could not find owner for resource {}: document={}, blog={}",
                        resourceId,
                        e1.getMessage(),
                        e2.getMessage());
                return null;
            }
        }
    }

    @Transactional
    public RatingResponse createOrUpdateRating(RatingRequest request, String userId) {
        Optional<Rating> existingRating = ratingRepository.findByResourceIdAndUserId(request.getResourceId(), userId);

        Double oldScore = existingRating.map(Rating::getScore).orElse(null);
        Rating rating;
        if (existingRating.isPresent()) {
            rating = existingRating.get();
            rating.setScore(request.getScore());
        } else {
            rating = Rating.builder()
                    .resourceId(request.getResourceId())
                    .userId(userId)
                    .score(request.getScore())
                    .build();
        }

        rating = ratingRepository.save(rating);

        // Points hook: update owner points based on score delta
        String ownerId = getOwnerId(request.getResourceId());
        if (ownerId != null) {
            long oldPoints = getPointsForScore(oldScore);
            long newPoints = getPointsForScore(request.getScore());
            long delta = newPoints - oldPoints;

            if (delta != 0) {
                try {
                    profileClient.updatePoints(ownerId, delta);
                } catch (Exception e) {
                    log.error("Failed to update points for rating update: profile={}, delta={}", ownerId, delta, e);
                }
            }
        }

        return toRatingResponse(rating);
    }

    public Page<RatingResponse> getRatingsByResource(String resourceId, Pageable pageable) {
        return ratingRepository.findByResourceId(resourceId, pageable).map(this::toRatingResponse);
    }

    public Double getAverageRating(String resourceId) {
        Double avg = ratingRepository.getAverageScoreByResourceId(resourceId);
        return avg != null ? avg : 0.0;
    }

    public RankingStatsResponse getRankingStats() {
        Double globalAvg = ratingRepository.getGlobalAverageScore();
        List<ResourceRatingStatsResponse> stats = ratingRepository.getResourceRatingStats();

        return RankingStatsResponse.builder()
                .globalAverage(globalAvg != null ? globalAvg : 0.0)
                .stats(stats)
                .build();
    }

    public RatingResponse getUserRatingForResource(String resourceId, String userId) {
        return ratingRepository
                .findByResourceIdAndUserId(resourceId, userId)
                .map(this::toRatingResponse)
                .orElse(null);
    }

    private RatingResponse toRatingResponse(Rating rating) {
        return RatingResponse.builder()
                .id(rating.getId())
                .resourceId(rating.getResourceId())
                .userId(rating.getUserId())
                .score(rating.getScore())
                .createdAt(rating.getCreatedAt())
                .updatedAt(rating.getUpdatedAt())
                .build();
    }
}
