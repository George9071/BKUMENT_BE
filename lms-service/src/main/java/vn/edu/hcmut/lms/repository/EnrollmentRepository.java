package vn.edu.hcmut.lms.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.edu.hcmut.lms.entity.Enrollment;

import java.util.List;

@Repository
public interface EnrollmentRepository extends JpaRepository<Enrollment, String> {
    boolean existsByClassRoomIdAndStudentProfileId(String classId, String studentProfileId);

    List<Enrollment> findByClassRoomId(String classId);

    List<Enrollment> findByStudentProfileId(String studentProfileId);
}
