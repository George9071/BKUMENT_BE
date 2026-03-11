package vn.edu.hcmut.social.service;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import vn.edu.hcmut.social.dto.request.RatingRequest;
import vn.edu.hcmut.social.dto.response.RatingResponse;
import vn.edu.hcmut.social.entity.Rating;
import vn.edu.hcmut.social.repository.RatingRepository;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RatingService {
    RatingRepository ratingRepository;

    @Transactional
    public RatingResponse createOrUpdateRating(RatingRequest request, String userId) {
        Optional<Rating> existingRating = ratingRepository.findByResourceIdAndUserId(request.getResourceId(), userId);

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

        // Call FeignClient to other service (as requested placeholder)
        callExternalServicePlaceholder(request.getResourceId(), request.getScore());

        return toRatingResponse(rating);
    }

    // Method rỗng để tự implement logic gọi FeignClient sang service khác sau
    private void callExternalServicePlaceholder(String resourceId, Double score) {
        // TODO: Implement FeignClient call here
    }

    public Page<RatingResponse> getRatingsByResource(String resourceId, Pageable pageable) {
        return ratingRepository.findByResourceId(resourceId, pageable).map(this::toRatingResponse);
    }

    public Double getAverageRating(String resourceId) {
        Double avg = ratingRepository.getAverageScoreByResourceId(resourceId);
        return avg != null ? avg : 0.0;
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
