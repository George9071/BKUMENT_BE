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

@Repository
public interface ClassRoomRepository extends JpaRepository<ClassRoom, String> {
    /**
     * Retrieves a paginated list of classes managed by a specific tutor.
     */
    Page<ClassRoom> findByTutorId(String tutorId, Pageable pageable);

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
            "(:keyword IS NULL OR LOWER(FUNCTION('unaccent', c.name)) LIKE :keyword OR LOWER(FUNCTION('unaccent', tu.name)) LIKE :keyword) AND " +
            "c.status = 'ENROLLING'")
    List<ClassRoom> searchAvailableClasses(
            @Param("subjectName") String subjectName,
            @Param("topicName") String topicName,
            @Param("format") LearningFormat format,
            @Param("keyword") String keyword
    );

}
