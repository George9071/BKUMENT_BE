package vn.edu.hcmut.social.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import vn.edu.hcmut.social.entity.Rating;

@Repository
public interface RatingRepository extends JpaRepository<Rating, String> {
    Page<Rating> findByResourceId(String resourceId, Pageable pageable);

    Optional<Rating> findByResourceIdAndUserId(String resourceId, String userId);

    @Query("SELECT AVG(r.score) FROM Rating r WHERE r.resourceId = :resourceId")
    Double getAverageScoreByResourceId(@Param("resourceId") String resourceId);
}
