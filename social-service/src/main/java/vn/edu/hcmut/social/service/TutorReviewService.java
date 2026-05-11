package vn.edu.hcmut.social.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import vn.edu.hcmut.social.dto.request.TutorReviewRequest;
import vn.edu.hcmut.social.dto.request.internal.InternalTutorRatingRequest;
import vn.edu.hcmut.social.dto.response.TutorReviewResponse;
import vn.edu.hcmut.social.dto.response.TutorReviewSummaryResponse;
import vn.edu.hcmut.social.dto.response.TutorStatsProjection;
import vn.edu.hcmut.social.entity.UserReviewTutor;
import vn.edu.hcmut.social.exception.AppException;
import vn.edu.hcmut.social.exception.ErrorCode;
import vn.edu.hcmut.social.repository.UserReviewTutorRepository;
import vn.edu.hcmut.social.repository.httpclient.LmsClient;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class TutorReviewService {

    UserReviewTutorRepository userReviewTutorRepository;
    LmsClient lmsClient;
    ApplicationEventPublisher eventPublisher;

    /**
     * Creates a review for a tutor.
     * Self-review block: a tutor account that is also a student cannot review themselves.
     */
    @Transactional
    public TutorReviewResponse createReview(TutorReviewRequest request, String userId) {
        if (userId.equals(request.getTutorId())) throw new AppException(ErrorCode.CANNOT_REVIEW_SELF);

        Optional<UserReviewTutor> existing =
                userReviewTutorRepository.findByUserIdAndTutorId(userId, request.getTutorId());
        if (existing.isPresent()) throw new AppException(ErrorCode.ALREADY_RATED);

        UserReviewTutor review = UserReviewTutor.builder()
                .tutorId(request.getTutorId())
                .userId(userId)
                .comment(request.getComment())
                .score(request.getScore())
                .build();

        try {
            review = userReviewTutorRepository.save(review);
        } catch (DataIntegrityViolationException e) {
            log.debug("Concurrent duplicate review attempt by user {} for tutor {}",
                    userId, request.getTutorId());
            throw new AppException(ErrorCode.ALREADY_RATED);
        }

        // Schedule LMS sync to fire AFTER this transaction commit
        eventPublisher.publishEvent(new TutorStatsChangedEvent(request.getTutorId()));

        return toTutorReviewResponse(review);
    }

    /**
     * Updates an existing review.
     */
    @Transactional
    public TutorReviewResponse updateReview(String reviewId, TutorReviewRequest request, String userId) {
        UserReviewTutor review = userReviewTutorRepository
                .findById(reviewId)
                .orElseThrow(() -> new AppException(ErrorCode.TUTOR_REVIEW_NOT_FOUND));

        if (!review.getUserId().equals(userId)) throw new AppException(ErrorCode.UNAUTHORIZED);

        if (request.getComment() != null)   review.setComment(request.getComment());
        if (request.getScore() != null)     review.setScore(request.getScore());

        review = userReviewTutorRepository.save(review);

        eventPublisher.publishEvent(new TutorStatsChangedEvent(review.getTutorId()));

        return toTutorReviewResponse(review);
    }

    /** Deletes a review and re-syncs the tutor's stats. */
    @Transactional
    public void deleteReview(String reviewId, String userId) {
        UserReviewTutor review = userReviewTutorRepository
                .findById(reviewId)
                .orElseThrow(() -> new AppException(ErrorCode.TUTOR_REVIEW_NOT_FOUND));

        if (!review.getUserId().equals(userId)) throw new AppException(ErrorCode.UNAUTHORIZED);

        String tutorId = review.getTutorId();
        userReviewTutorRepository.delete(review);

        eventPublisher.publishEvent(new TutorStatsChangedEvent(tutorId));
    }

    public record TutorStatsChangedEvent(String tutorId) {}

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void syncTutorStats(TutorStatsChangedEvent event) {
        String tutorId = event.tutorId();

        try {
            TutorStatsProjection stats = userReviewTutorRepository.getTutorStats(tutorId);
            double avg = (stats != null && stats.getAverageScore() != null)
                    ? stats.getAverageScore() : 0.0;
            long count = (stats != null && stats.getReviewCount() != null)
                    ? stats.getReviewCount() : 0L;

            InternalTutorRatingRequest req = InternalTutorRatingRequest.builder()
                    .averageRating(avg)
                    .ratingCount(count)
                    .build();

            lmsClient.updateTutorRating(tutorId, req);
            log.info("Synced tutor stats: id={}, avg={}, count={}", tutorId, avg, count);
        } catch (Exception e) {
            log.error("Failed to sync tutor stats for {}: {}", tutorId, e.getMessage());
        }
    }

    public Page<TutorReviewResponse> getReviewsByTutor(String tutorId, Pageable pageable) {
        return userReviewTutorRepository.findByTutorId(tutorId, pageable).map(this::toTutorReviewResponse);
    }

    /**
     * Returns avg, total, and per-score distribution for a tutor.
     */
    public TutorReviewSummaryResponse getSummary(String tutorId) {
        List<Object[]> rows = userReviewTutorRepository.countReviewsGroupByScore(tutorId);

        // Pre-fill all 5 score buckets so the UI never sees a missing key.
        Map<Integer, Long> ratingCounts = new HashMap<>();
        for (int i = 1; i <= 5; i++) ratingCounts.put(i, 0L);

        long total = 0L;
        double weightedSum = 0.0;

        for (Object[] row : rows) {
            Double score = (Double) row[0];
            Long count = (Long) row[1];
            if (score == null || count == null) continue;

            int bucket = (int) Math.round(score);
            bucket = Math.max(1, Math.min(5, bucket));
            ratingCounts.merge(bucket, count, Long::sum);

            total += count;
            weightedSum += score * count;
        }

        double avg = total > 0 ? weightedSum / total : 0.0;

        return TutorReviewSummaryResponse.builder()
                .tutorId(tutorId)
                .averageScore(avg)
                .totalReviews(total)
                .ratingCounts(ratingCounts)
                .build();
    }

    public TutorReviewResponse getReviewByUserAndTutor(String userId, String tutorId) {
        return userReviewTutorRepository
                .findByUserIdAndTutorId(userId, tutorId)
                .map(this::toTutorReviewResponse)
                .orElseThrow(() -> new AppException(ErrorCode.TUTOR_REVIEW_NOT_FOUND));
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
