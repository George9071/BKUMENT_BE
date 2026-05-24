package vn.edu.hcmut.social.dto.response;

public interface ClassReviewStatsProjection {

    /** Average review score for the class, or null if no reviews exist. */
    Double getAverageScore();

    /** Total number of reviews for the class; never null (COUNT returns 0 on empty). */
    Long getReviewCount();
}
