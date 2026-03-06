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
    boolean existsByClassRoomIdAndStudentProfileId(String classId, String studentProfileId);

    Optional<Enrollment> findByClassRoomIdAndStudentProfileId(String classId, String studentId);
    List<Enrollment> findByClassRoomIdAndStatus(String classRoomId, EnrollmentStatus status);

    @Query("""
        SELECT e.classRoom FROM Enrollment e\s
        WHERE e.studentProfileId = :studentId\s
        AND e.status = :enrollmentStatus
        AND e.classRoom.status IN :classStatuses
   \s""")
    List<ClassRoom> findActiveClassesByStudent(
            @Param("studentId") String studentId,
            @Param("enrollmentStatus") EnrollmentStatus enrollmentStatus,
            @Param("classStatuses") List<ClassStatus> classStatuses
    );

    @Query("SELECT e FROM Enrollment e WHERE e.studentProfileId = :profileId " +
            "AND (:status IS NULL OR e.status = :status)")
    Page<Enrollment> findByStudentProfileIdAndStatus(
            @Param("profileId") String profileId,
            @Param("status") EnrollmentStatus status,
            Pageable pageable);
}
