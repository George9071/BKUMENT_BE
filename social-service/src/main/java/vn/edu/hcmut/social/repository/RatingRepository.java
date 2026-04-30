package vn.edu.hcmut.social.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmut.social.dto.response.ResourceRatingStatsResponse;
import vn.edu.hcmut.social.entity.Rating;

@Repository
public interface RatingRepository extends JpaRepository<Rating, String> {
    Page<Rating> findByResourceId(String resourceId, Pageable pageable);

    Optional<Rating> findByResourceIdAndUserId(String resourceId, String userId);

    @Query("SELECT AVG(r.score) FROM Rating r WHERE r.resourceId = :resourceId")
    Double getAverageScoreByResourceId(@Param("resourceId") String resourceId);

    @Query(
            "SELECT new vn.edu.hcmut.social.dto.response.ResourceRatingStatsResponse(r.resourceId, AVG(r.score), COUNT(r)) "
                    + "FROM Rating r GROUP BY r.resourceId")
    List<ResourceRatingStatsResponse> getResourceRatingStats();

    @Query("SELECT AVG(r.score) FROM Rating r")
    Double getGlobalAverageScore();

//    void deleteByResourceId(String resourceId);

    /**
     * Hard-deletes all ratings for a removed resource in a single statement.
     * Returns the number of rows deleted (used for logging).
     *
     * Called by ReportService.executeContentRemoval when a DOCUMENT or BLOG is
     * removed by moderation. Idempotent — re-running with the same resourceId
     * after the rows are gone is a no-op.
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM Rating r WHERE r.resourceId = :resourceId")
    int deleteByResourceId(@Param("resourceId") String resourceId);
}
