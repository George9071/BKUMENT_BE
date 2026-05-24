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
import vn.edu.hcmut.social.dto.request.ClassReviewRequest;
import vn.edu.hcmut.social.dto.request.ClassReviewUpdateRequest;
import vn.edu.hcmut.social.dto.request.internal.InternalClassRatingRequest;
import vn.edu.hcmut.social.dto.response.APIResponse;
import vn.edu.hcmut.social.dto.response.ClassReviewResponse;
import vn.edu.hcmut.social.dto.response.ClassReviewStatsProjection;
import vn.edu.hcmut.social.dto.response.ClassReviewSummaryResponse;
import vn.edu.hcmut.social.dto.response.ClassRoomResponse;
import vn.edu.hcmut.social.entity.ClassReview;
import vn.edu.hcmut.social.exception.AppException;
import vn.edu.hcmut.social.exception.ErrorCode;
import vn.edu.hcmut.social.mapper.ClassReviewMapper;
import vn.edu.hcmut.social.repository.ClassReviewRepository;
import vn.edu.hcmut.social.repository.httpclient.LmsClient;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ClassReviewService {

    ClassReviewRepository classReviewRepository;
    ClassReviewMapper classReviewMapper;
    LmsClient lmsClient;
    ApplicationEventPublisher eventPublisher;

    /**
     * Creates a review for a class.
     * Only approved students may review; class owners are blocked.
     */
    @Transactional
    public ClassReviewResponse createReview(ClassReviewRequest request, String userId) {
        validateReviewEligibility(request.getClassId(), userId);

        Optional<ClassReview> existing =
                classReviewRepository.findByUserIdAndClassId(userId, request.getClassId());
        if (existing.isPresent()) throw new AppException(ErrorCode.ALREADY_RATED);

        ClassReview review = ClassReview.builder()
                .classId(request.getClassId())
                .userId(userId)
                .comment(request.getComment())
                .score(request.getScore())
                .build();

        try {
            review = classReviewRepository.save(review);
        } catch (DataIntegrityViolationException e) {
            log.debug("Concurrent duplicate review attempt by user {} for class {}",
                    userId, request.getClassId());
            throw new AppException(ErrorCode.ALREADY_RATED);
        }

        eventPublisher.publishEvent(new ClassRatingChangedEvent(request.getClassId()));

        return classReviewMapper.toResponse(review);
    }

    /**
     * Updates an existing review.
     */
    @Transactional
    public ClassReviewResponse updateReview(String reviewId, ClassReviewUpdateRequest request, String userId) {
        ClassReview review = classReviewRepository
                .findById(reviewId)
                .orElseThrow(() -> new AppException(ErrorCode.CLASS_REVIEW_NOT_FOUND));

        if (!review.getUserId().equals(userId)) throw new AppException(ErrorCode.UNAUTHORIZED);

        validateReviewEligibility(review.getClassId(), userId);

        if (request.getComment() != null) review.setComment(request.getComment());
        if (request.getScore() != null) review.setScore(request.getScore());

        review = classReviewRepository.save(review);

        eventPublisher.publishEvent(new ClassRatingChangedEvent(review.getClassId()));

        return classReviewMapper.toResponse(review);
    }

    /** Deletes a review and re-syncs the class rating stats. */
    @Transactional
    public void deleteReview(String reviewId, String userId) {
        ClassReview review = classReviewRepository
                .findById(reviewId)
                .orElseThrow(() -> new AppException(ErrorCode.CLASS_REVIEW_NOT_FOUND));

        if (!review.getUserId().equals(userId)) throw new AppException(ErrorCode.UNAUTHORIZED);

        String classId = review.getClassId();
        classReviewRepository.delete(review);

        eventPublisher.publishEvent(new ClassRatingChangedEvent(classId));
    }

    public record ClassRatingChangedEvent(String classId) {}

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void syncClassStats(ClassRatingChangedEvent event) {
        String classId = event.classId();

        try {
            ClassReviewStatsProjection stats = classReviewRepository.getClassStats(classId);
            double avg = (stats != null && stats.getAverageScore() != null)
                    ? stats.getAverageScore() : 0.0;
            long count = (stats != null && stats.getReviewCount() != null)
                    ? stats.getReviewCount() : 0L;

            InternalClassRatingRequest req = InternalClassRatingRequest.builder()
                    .averageRating(avg)
                    .ratingCount(toRatingCount(count))
                    .build();

            lmsClient.updateClassRating(classId, req);
            log.info("Synced class rating stats: id={}, avg={}, count={}", classId, avg, count);
        } catch (Exception e) {
            log.error("Failed to sync class rating stats for {}: {}", classId, e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public Page<ClassReviewResponse> getReviewsByClass(String classId, Pageable pageable) {
        return classReviewRepository.findByClassId(classId, pageable).map(classReviewMapper::toResponse);
    }

    /**
     * Returns avg, total, and per-score distribution for a class.
     */
    @Transactional(readOnly = true)
    public ClassReviewSummaryResponse getSummary(String classId) {
        List<Object[]> rows = classReviewRepository.countReviewsGroupByScore(classId);

        Map<Integer, Long> ratingCounts = emptyRatingBuckets();
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

        return ClassReviewSummaryResponse.builder()
                .classId(classId)
                .averageScore(avg)
                .totalReviews(total)
                .ratingCounts(ratingCounts)
                .build();
    }

    @Transactional(readOnly = true)
    public ClassReviewResponse getReviewByUserAndClass(String userId, String classId) {
        return classReviewRepository
                .findByUserIdAndClassId(userId, classId)
                .map(classReviewMapper::toResponse)
                .orElseThrow(() -> new AppException(ErrorCode.CLASS_REVIEW_NOT_FOUND));
    }

    private void validateReviewEligibility(String classId, String userId) {
        ClassRoomResponse classRoom = fetchClassRoom(classId);

        if (userId.equals(classRoom.getTutorId()) || "OWNER".equals(classRoom.getUserStatus())) {
            throw new AppException(ErrorCode.CANNOT_REVIEW_OWN_CLASS);
        }
        if (!"APPROVED".equals(classRoom.getUserStatus())) {
            throw new AppException(ErrorCode.CLASS_REVIEW_REQUIRES_APPROVED_ENROLLMENT);
        }
    }

    private ClassRoomResponse fetchClassRoom(String classId) {
        try {
            APIResponse<ClassRoomResponse> response = lmsClient.getClassRoom(classId);
            if (response == null || response.getResult() == null) {
                throw new AppException(ErrorCode.CLASS_NOT_FOUND);
            }
            return response.getResult();
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Failed to fetch class {} from LMS: {}", classId, e.getMessage());
            throw new AppException(ErrorCode.CLASS_NOT_FOUND);
        }
    }

    private Map<Integer, Long> emptyRatingBuckets() {
        Map<Integer, Long> ratingCounts = new HashMap<>();
        for (int i = 1; i <= 5; i++) ratingCounts.put(i, 0L);
        return ratingCounts;
    }

    private int toRatingCount(long count) {
        if (count > Integer.MAX_VALUE) {
            log.warn("Class review count {} exceeds Integer.MAX_VALUE; clamping before LMS sync", count);
            return Integer.MAX_VALUE;
        }
        return (int) count;
    }
}
