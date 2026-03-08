package vn.edu.hcmut.lms.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import vn.edu.hcmut.lms.entity.Subject;

import java.util.Collection;

@Repository
public interface SubjectRepository extends JpaRepository<Subject, String> {
    @EntityGraph(attributePaths = {"topics"})
    @Query("SELECT s FROM Subject s")
    Page<Subject> findAllWithTopics(Pageable pageable);

    Page<Subject> findByIdIn(Collection<String> ids, Pageable pageable);
}
