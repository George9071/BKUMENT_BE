package vn.edu.hcmut.social.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import vn.edu.hcmut.social.dto.response.ClassReviewStatsProjection;
import vn.edu.hcmut.social.entity.ClassReview;

@Repository
public interface ClassReviewRepository extends JpaRepository<ClassReview, String> {
    Page<ClassReview> findByClassId(String classId, Pageable pageable);

    Optional<ClassReview> findByUserIdAndClassId(String userId, String classId);

    long countByClassId(String classId);

    @Query("SELECT AVG(r.score) FROM ClassReview r WHERE r.classId = :classId")
    Double getAverageScoreByClassId(String classId);

    @Query("SELECT r.score, COUNT(r) FROM ClassReview r WHERE r.classId = :classId GROUP BY r.score")
    List<Object[]> countReviewsGroupByScore(String classId);

    @Query("""
             SELECT AVG(r.score) AS averageScore, COUNT(r) AS reviewCount
             FROM ClassReview r
             WHERE r.classId = :classId
             """)
    ClassReviewStatsProjection getClassStats(@Param("classId") String classId);
}
