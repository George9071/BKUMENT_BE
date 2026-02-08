package vn.edu.hcmut.lms.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.edu.hcmut.lms.entity.Subject;

@Repository
public interface SubjectRepository extends JpaRepository<Subject, String> {
}
