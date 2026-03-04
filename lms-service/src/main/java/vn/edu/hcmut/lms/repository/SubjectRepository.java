package vn.edu.hcmut.lms.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import vn.edu.hcmut.lms.entity.Subject;

import java.util.List;

@Repository
public interface SubjectRepository extends JpaRepository<Subject, String> {
    @Query("SELECT DISTINCT s FROM Subject s LEFT JOIN FETCH s.topics")
    List<Subject> findAllWithTopics();
}
