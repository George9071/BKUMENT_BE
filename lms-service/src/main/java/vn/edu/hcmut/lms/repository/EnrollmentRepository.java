package vn.edu.hcmut.lms.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.edu.hcmut.lms.constant.ClassStatus;
import vn.edu.hcmut.lms.constant.EnrollmentStatus;
import vn.edu.hcmut.lms.entity.ClassRoom;
import vn.edu.hcmut.lms.entity.Enrollment;

import java.util.List;
import java.util.Optional;

@Repository
public interface EnrollmentRepository extends JpaRepository<Enrollment, String> {

    Optional<Enrollment> findByClassRoomIdAndStudentProfileId(String classId, String studentId);

    Page<Enrollment> findByClassRoomIdAndStatus(String classRoomId, EnrollmentStatus status, Pageable pageable);
    @Query("""
        SELECT c FROM Enrollment e 
        JOIN e.classRoom c 
        LEFT JOIN FETCH c.schedules 
        WHERE e.studentProfileId = :studentId 
        AND e.status = :enrollmentStatus
        AND c.status IN :classStatuses
    """)
    List<ClassRoom> findActiveClassesByStudent(
            @Param("studentId") String studentId,
            @Param("enrollmentStatus") EnrollmentStatus enrollmentStatus,
            @Param("classStatuses") List<ClassStatus> classStatuses
    );

    @Query(value = "SELECT e FROM Enrollment e " +
            "JOIN FETCH e.classRoom c " +
            "JOIN FETCH c.tutor " +
            "WHERE e.studentProfileId = :profileId " +
            "AND (:status IS NULL OR e.status = :status)",
            countQuery = "SELECT count(e) FROM Enrollment e " +
                    "WHERE e.studentProfileId = :profileId " +
                    "AND (:status IS NULL OR e.status = :status)")
    Page<Enrollment> findByStudentProfileIdAndStatus(
            @Param("profileId") String profileId,
            @Param("status") EnrollmentStatus status,
            Pageable pageable);

    List<Enrollment> findByStudentProfileIdAndClassRoomIdIn(String studentProfileId, List<String> classRoomIds);

    void deleteByClassRoomIdIn(List<String> classRoomIds);

    @Query("SELECT e.classRoom.id as classId, count(e) as count " +
            "FROM Enrollment e " +
            "WHERE e.classRoom.id IN :classIds " +
            "AND e.status = vn.edu.hcmut.lms.constant.EnrollmentStatus.APPROVED " +
            "GROUP BY e.classRoom.id")
    List<Object[]> countByClassRoomIdIn(@Param("classIds") List<String> classIds);
}
