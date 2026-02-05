package vn.edu.hcmut.lms.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.edu.hcmut.lms.constant.ClassStatus;
import vn.edu.hcmut.lms.entity.ClassRoom;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClassRoomRepository extends JpaRepository<ClassRoom, String> {
    Optional<ClassRoom> findByTutorId(String id);

    @Query("""
        SELECT c FROM ClassRoom c\s
        WHERE c.tutor.id = :tutorId\s
        AND c.status IN :statuses
   \s""")
    List<ClassRoom> findActiveClassesByTutor(
            @Param("tutorId") String tutorId,
            @Param("statuses") List<ClassStatus> statuses
    );
}
