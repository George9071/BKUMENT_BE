package vn.edu.hcmut.social.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
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
import vn.edu.hcmut.social.dto.response.ResourceEngagementStatsResponse;
import vn.edu.hcmut.social.dto.response.ResourceRatingStatsResponse;
import vn.edu.hcmut.social.entity.Rating;
import vn.edu.hcmut.social.exception.AppException;
import vn.edu.hcmut.social.exception.ErrorCode;
import vn.edu.hcmut.social.repository.CommentRepository;
import vn.edu.hcmut.social.repository.RatingRepository;
import vn.edu.hcmut.social.repository.httpclient.BlogClient;
import vn.edu.hcmut.social.repository.httpclient.DocumentClient;
import vn.edu.hcmut.social.repository.httpclient.ProfileClient;

/**
 * Manages resource ratings (documents and blog posts) within the social-service.
 * * * *
 * Core responsibilities:
 *   - Submit a one-time rating for any rateable resource.
 *   - Calculate and expose aggregate rating statistics used by the document ranking formula
 *     in the document-service.
 *   - Award or deduct gamification points on the owner's profile based on the score received.
 *   - Compute combined engagement stats (ratings + comments) for admin dashboards.
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class RatingService {
    RatingRepository ratingRepository;
    CommentRepository commentRepository;
    ProfileClient profileClient;
    DocumentClient documentClient;
    BlogClient blogClient;

    /**
     * Maps a rating score to the gamification point delta awarded to the resource owner.
     *   ≥ 4.5 -> +2
     *   ≥ 3.5 -> +1
     *   ≥ 2.5 -> 0
     *   ≥ 1.5 -> -1
     *   < 1.5 -> -2
     */
    private Long getPointsForScore(Double score) {
        if (score == null) return 0L;
        if (score >= 4.5) return 2L;
        if (score >= 3.5) return 1L;
        if (score >= 2.5) return 0L;
        if (score >= 1.5) return -1L;
        return -2L;
    }

    /**
     * OPTIMIZATION — type-based routing preferred:
     *   If RatingRequest carried a ResourceType field (DOCUMENT / BLOG), the fallback chain
     *   could be eliminated and replaced by a direct targeted call, halving the latency on
     *   blog resource lookups and removing unnecessary cross-service HTTP errors.
     *
     * @param resourceId the ID of the rated resource
     * @return the owner's profile ID, or null if neither service recognises the resource
     */
    private String getOwnerId(String resourceId) {
        try {
            return documentClient.getOwnerId(resourceId);
        } catch (Exception e1) {
            try {
                return blogClient.getOwnerId(resourceId);
            } catch (Exception e2) {
                log.warn(
                        "Could not find owner for resource {}: document-service='{}', blog-service='{}'",
                        resourceId,
                        e1.getMessage(),
                        e2.getMessage());
                return null;
            }
        }
    }

    /**
     * Records a new rating for a resource.
     * NOTE: Updates are not supported — re-rating the same resource throws ALREADY_RATED.
     * * * *
     * @param request the payload (resourceId, score 1.0-5.0)
     * @param userId  the authenticated user's profile ID (from JWT)
     */
    @Transactional
    public RatingResponse createOrUpdateRating(RatingRequest request, String userId) {
        Optional<Rating> existing = ratingRepository.findByResourceIdAndUserId(request.getResourceId(), userId);
        if (existing.isPresent()) throw new AppException(ErrorCode.ALREADY_RATED);

        // Self-rating guard: resolve the owner and reject if the rater is the owner.
        String ownerId = getOwnerId(request.getResourceId());
        if (ownerId == null) {
            throw new AppException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (userId.equals(ownerId)) {
            throw new AppException(ErrorCode.CANNOT_RATE_OWN_RESOURCE);
        }

        Rating rating = Rating.builder()
                .resourceId(request.getResourceId())
                .userId(userId)
                .score(request.getScore())
                .build();


        try {
            rating = ratingRepository.save(rating);
        } catch (DataIntegrityViolationException e) {
            log.debug("Concurrent duplicate rating attempt by user {} for resource {}",
                    userId, request.getResourceId());
            throw new AppException(ErrorCode.ALREADY_RATED);
        }

        long delta = getPointsForScore(request.getScore());
        if (delta != 0) {
            try {
                profileClient.updatePoints(ownerId, delta);
            } catch (Exception e) {
                log.error("Failed to update points for owner={}, delta={}", ownerId, delta, e);
            }
        }

        return toRatingResponse(rating);
    }

    @Transactional(readOnly = true)
    public Page<RatingResponse> getRatingsByResource(String resourceId, Pageable pageable) {
        return ratingRepository.findByResourceId(resourceId, pageable).map(this::toRatingResponse);
    }

    @Transactional(readOnly = true)
    public Double getAverageRating(String resourceId) {
        Double avg = ratingRepository.getAverageScoreByResourceId(resourceId);
        return avg != null ? avg : 0.0;
    }

    /** Aggregate stats consumed by document-service for the ranking score formula. */
    @Transactional(readOnly = true)
    public RankingStatsResponse getRankingStats() {
        Double globalAvg = ratingRepository.getGlobalAverageScore();
        List<ResourceRatingStatsResponse> stats = ratingRepository.getResourceRatingStats();

        return RankingStatsResponse.builder()
                .globalAverage(globalAvg != null ? globalAvg : 0.0)
                .stats(stats)
                .build();
    }

    /**
     * Combined rating + comment engagement stats for admin dashboards.
     */
    @Transactional(readOnly = true)
    public List<ResourceEngagementStatsResponse> getEngagementStats() {
        List<ResourceRatingStatsResponse> ratingStats = ratingRepository.getResourceRatingStats();
        List<Object[]> commentCounts = commentRepository.countCommentsGroupByResourceId();

        Map<String, Long> commentMap = commentCounts.stream()
                .collect(Collectors.toMap(row -> (String) row[0], row -> (Long) row[1]));

        Map<String, ResourceEngagementStatsResponse> result = new HashMap<>();

        // every rated resource — pull comment count from the map (default 0).
        for (ResourceRatingStatsResponse rs : ratingStats) {
            result.put(rs.getResourceId(), ResourceEngagementStatsResponse.builder()
                    .resourceId(rs.getResourceId())
                    .averageRating(rs.getAverageRating())
                    .ratingCount(rs.getRatingCount())
                    .commentCount(commentMap.getOrDefault(rs.getResourceId(), 0L))
                    .build());
        }

        // commented-only resources that did not appear in ratingStats.
        for (Map.Entry<String, Long> entry : commentMap.entrySet()) {
            result.putIfAbsent(entry.getKey(), ResourceEngagementStatsResponse.builder()
                    .resourceId(entry.getKey())
                    .averageRating(0.0)
                    .ratingCount(0L)
                    .commentCount(entry.getValue())
                    .build());
        }

        return new java.util.ArrayList<>(result.values());
    }

    /**
     * Returns the user's rating for a resource.
     * @return Optional.of(rating) if the user has rated this resource, else Optional.empty()
     */
    @Transactional(readOnly = true)
    public Optional<RatingResponse> getUserRatingForResource(String resourceId, String userId) {
        return ratingRepository
                .findByResourceIdAndUserId(resourceId, userId)
                .map(this::toRatingResponse);
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
