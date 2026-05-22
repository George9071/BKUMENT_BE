package vn.edu.hcmut.lms.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.edu.hcmut.lms.constant.ClassStatus;
import vn.edu.hcmut.lms.constant.LearningFormat;
import vn.edu.hcmut.lms.entity.ClassRoom;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClassRoomRepository extends JpaRepository<ClassRoom, String> {
    /**
     * Retrieves a paginated list of classes managed by a specific tutor.
     */
    Page<ClassRoom> findByTutorId(String tutorId, Pageable pageable);

    /**
     * Retrieves all classes managed by a specific tutor.
     */
    List<ClassRoom> findByTutorId(String tutorId);

    /**
     * Deletes all classes managed by a specific tutor.
     */
    void deleteByTutorId(String tutorId);

    /**
     * Retrieves all active classes (e.g., ENROLLING, ONGOING) for a specific tutor.
     */
    @Query("""
        SELECT c FROM ClassRoom c\s
        WHERE c.tutor.id = :tutorId\s
        AND c.status IN :statuses
   \s""")
    List<ClassRoom> findActiveClassesByTutor(
            @Param("tutorId") String tutorId,
            @Param("statuses") List<ClassStatus> statuses
    );

    /**
     * Searches for available enrolling classes with dynamic filters.
     */
    @Query("SELECT c FROM ClassRoom c " +
            "JOIN FETCH c.tutor tu " +
            "LEFT JOIN c.topic t " +
            "LEFT JOIN t.subject s " +
            "WHERE " +
            "(:subjectName IS NULL OR LOWER(FUNCTION('unaccent', s.name)) LIKE :subjectName) AND " +
            "(:topicName IS NULL OR LOWER(FUNCTION('unaccent', t.name)) LIKE :topicName) AND " +
            "(:format IS NULL OR c.format = :format) AND " +
            "(:keyword IS NULL OR LOWER(FUNCTION('unaccent', c.name)) LIKE :keyword OR LOWER(FUNCTION('unaccent', tu.name)) LIKE :keyword OR LOWER(FUNCTION('unaccent', c.description)) LIKE :keyword) AND " +
            "c.status = vn.edu.hcmut.lms.constant.ClassStatus.ENROLLING " +
            "ORDER BY COALESCE(tu.averageRating, 0) DESC, c.name ASC")
    List<ClassRoom> searchAvailableClasses(
            @Param("subjectName") String subjectName,
            @Param("topicName") String topicName,
            @Param("format") LearningFormat format,
            @Param("keyword") String keyword,
            Pageable pageable
    );

    @Query("SELECT c FROM ClassRoom c " +
            "JOIN FETCH c.tutor " +
            "LEFT JOIN FETCH c.topic t " +
            "LEFT JOIN FETCH t.subject s " +
            "LEFT JOIN FETCH c.schedules " +
            "WHERE c.id = :classId")
    Optional<ClassRoom> findClassRoomById(@Param("classId") String classId);

    @Query(value = """
        SELECT c.* FROM class_room c
        JOIN tutor t ON c.tutor_id = t.id
        LEFT JOIN (
            SELECT class_id, COUNT(*) as enroll_count\s
            FROM enrollments\s
            WHERE status = 'APPROVED'
            GROUP BY class_id
        ) e ON c.id = e.class_id
        WHERE c.status IN ('ENROLLING', 'ONGOING')
        ORDER BY (COALESCE(t.average_rating, 0) * 10 + COALESCE(e.enroll_count, 0)) DESC
    """, 
    countQuery = """
        SELECT count(*) FROM class_room c\s
        WHERE c.status IN ('ENROLLING', 'ONGOING')
    """,
    nativeQuery = true)
    Page<ClassRoom> findTopTrendingClasses(Pageable pageable);
}
