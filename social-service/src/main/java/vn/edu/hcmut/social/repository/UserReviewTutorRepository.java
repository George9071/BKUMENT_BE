package vn.edu.hcmut.social.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import vn.edu.hcmut.social.dto.response.TutorStatsProjection;
import vn.edu.hcmut.social.entity.UserReviewTutor;

@Repository
public interface UserReviewTutorRepository extends JpaRepository<UserReviewTutor, String> {
    Page<UserReviewTutor> findByTutorId(String tutorId, Pageable pageable);

    Optional<UserReviewTutor> findByUserIdAndTutorId(String userId, String tutorId);

    long countByTutorId(String tutorId);

    @Query("SELECT AVG(r.score) FROM UserReviewTutor r WHERE r.tutorId = :tutorId")
    Double getAverageScoreByTutorId(String tutorId);

    @Query("SELECT r.score, COUNT(r) FROM UserReviewTutor r WHERE r.tutorId = :tutorId GROUP BY r.score")
    List<Object[]> countReviewsGroupByScore(String tutorId);

     @Query("""
             SELECT AVG(r.score) AS averageScore, COUNT(r) AS reviewCount
             FROM UserReviewTutor r
             WHERE r.tutorId = :tutorId
             """)
     TutorStatsProjection getTutorStats(@Param("tutorId") String tutorId);
}
