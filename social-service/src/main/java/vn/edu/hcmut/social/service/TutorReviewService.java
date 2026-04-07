package vn.edu.hcmut.social.service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import vn.edu.hcmut.social.dto.request.TutorReviewRequest;
import vn.edu.hcmut.social.dto.request.internal.InternalTutorRatingRequest;
import vn.edu.hcmut.social.dto.response.TutorReviewResponse;
import vn.edu.hcmut.social.dto.response.TutorReviewSummaryResponse;
import vn.edu.hcmut.social.entity.UserReviewTutor;
import vn.edu.hcmut.social.exception.AppException;
import vn.edu.hcmut.social.exception.ErrorCode;
import vn.edu.hcmut.social.repository.UserReviewTutorRepository;
import vn.edu.hcmut.social.repository.httpclient.LmsClient;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class TutorReviewService {
    UserReviewTutorRepository userReviewTutorRepository;
    LmsClient lmsClient;

    @Transactional
    public TutorReviewResponse createReview(TutorReviewRequest request, String userId) {
        Optional<UserReviewTutor> existingReview =
                userReviewTutorRepository.findByUserIdAndTutorId(userId, request.getTutorId());

        if (existingReview.isPresent()) {
            throw new AppException(ErrorCode.ALREADY_RATED);
        }

        UserReviewTutor review = UserReviewTutor.builder()
                .tutorId(request.getTutorId())
                .userId(userId)
                .comment(request.getComment())
                .score(request.getScore())
                .build();

        review = userReviewTutorRepository.save(review);

        syncTutorStats(request.getTutorId());

        return toTutorReviewResponse(review);
    }

    @Transactional
    public TutorReviewResponse updateReview(String reviewId, TutorReviewRequest request, String userId) {
        UserReviewTutor review = userReviewTutorRepository
                .findById(reviewId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_EXISTED));

        if (!review.getUserId().equals(userId)) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        review.setComment(request.getComment());
        review.setScore(request.getScore());
        review.setUpdatedAt(LocalDateTime.now());

        review = userReviewTutorRepository.save(review);

        syncTutorStats(review.getTutorId());

        return toTutorReviewResponse(review);
    }

    @Transactional
    public void deleteReview(String reviewId, String userId) {
        UserReviewTutor review = userReviewTutorRepository
                .findById(reviewId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_EXISTED));

        if (!review.getUserId().equals(userId)) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        String tutorId = review.getTutorId();
        userReviewTutorRepository.delete(review);

        syncTutorStats(tutorId);
    }

    private void syncTutorStats(String tutorId) {
        try {
            Double avg = userReviewTutorRepository.getAverageScoreByTutorId(tutorId);
            long total = userReviewTutorRepository.countByTutorId(tutorId);

            InternalTutorRatingRequest statsRequest = InternalTutorRatingRequest.builder()
                    .averageRating(avg != null ? avg : 0.0)
                    .ratingCount((int) total)
                    .build();

            lmsClient.updateTutorRating(tutorId, statsRequest);
            log.info("Synced tutor stats for id {}: avg={}, count={}", tutorId, avg, total);
        } catch (Exception e) {
            log.error("Failed to sync tutor stats for id {}: {}", tutorId, e.getMessage());
        }
    }

    public Page<TutorReviewResponse> getReviewsByTutor(String tutorId, Pageable pageable) {
        return userReviewTutorRepository.findByTutorId(tutorId, pageable).map(this::toTutorReviewResponse);
    }

    public TutorReviewSummaryResponse getSummary(String tutorId) {
        Double avg = userReviewTutorRepository.getAverageScoreByTutorId(tutorId);
        long total = userReviewTutorRepository.countByTutorId(tutorId);
        List<Object[]> counts = userReviewTutorRepository.countReviewsGroupByScore(tutorId);

        Map<Integer, Long> ratingCounts = new HashMap<>();
        for (int i = 1; i <= 5; i++) {
            ratingCounts.put(i, 0L);
        }

        for (Object[] row : counts) {
            Double score = (Double) row[0];
            Long count = (Long) row[1];
            ratingCounts.put(score.intValue(), count);
        }

        return TutorReviewSummaryResponse.builder()
                .tutorId(tutorId)
                .averageScore(avg != null ? avg : 0.0)
                .totalReviews(total)
                .ratingCounts(ratingCounts)
                .build();
    }

    public TutorReviewResponse getReviewByUserAndTutor(String userId, String tutorId) {
        return userReviewTutorRepository
                .findByUserIdAndTutorId(userId, tutorId)
                .map(this::toTutorReviewResponse)
                .orElse(null);
    }

    private TutorReviewResponse toTutorReviewResponse(UserReviewTutor review) {
        return TutorReviewResponse.builder()
                .id(review.getId())
                .userId(review.getUserId())
                .tutorId(review.getTutorId())
                .comment(review.getComment())
                .score(review.getScore())
                .createdAt(review.getCreatedAt())
                .updatedAt(review.getUpdatedAt())
                .build();
    }
}
