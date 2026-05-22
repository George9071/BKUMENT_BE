package vn.edu.hcmut.lms.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.edu.hcmut.lms.entity.Tutor;

@Repository
public interface TutorRepository extends JpaRepository<Tutor, String> {
    @Query("SELECT t FROM Tutor t WHERE :subjectId MEMBER OF t.subjectIds AND t.status = 'ACTIVE'")
    Page<Tutor> findBySubjectId(String subjectId, Pageable pageable);
    Page<Tutor> findByStatus(String status, Pageable pageable);
}
