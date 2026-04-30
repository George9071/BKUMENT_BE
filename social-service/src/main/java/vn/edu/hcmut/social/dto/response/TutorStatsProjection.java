package vn.edu.hcmut.social.dto.response;

public interface TutorStatsProjection {

    /** Average review score for the tutor, or null if no reviews exist. */
    Double getAverageScore();

    /** Total number of reviews for the tutor; never null (COUNT returns 0 on empty). */
    Long getReviewCount();
}
