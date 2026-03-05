package vn.edu.hcmut.lms.repository;

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
    List<ClassRoom> findByTutorId(String id);

    @Query("""
        SELECT c FROM ClassRoom c\s
        WHERE c.tutor.id = :tutorId\s
        AND c.status IN :statuses
   \s""")
    List<ClassRoom> findActiveClassesByTutor(
            @Param("tutorId") String tutorId,
            @Param("statuses") List<ClassStatus> statuses
    );

    @Query("SELECT c FROM ClassRoom c " +
            "JOIN c.topic t " +
            "JOIN t.subject s " +
            "WHERE " +
            "(:subjectName IS NULL OR LOWER(FUNCTION('unaccent', s.name)) LIKE :subjectName) AND " +
            "(:topicName IS NULL OR LOWER(FUNCTION('unaccent', t.name)) LIKE :topicName) AND " +
            "(:format IS NULL OR c.format = :format) AND " +
            "(:keyword IS NULL OR LOWER(FUNCTION('unaccent', c.name)) LIKE :keyword OR LOWER(FUNCTION('unaccent', c.tutor.name)) LIKE :keyword) AND " +
            "c.status = 'ENROLLING'")
    List<ClassRoom> searchAvailableClasses(
            @Param("subjectName") String subjectName,
            @Param("topicName") String topicName,
            @Param("format") LearningFormat format,
            @Param("keyword") String keyword
    );

}
