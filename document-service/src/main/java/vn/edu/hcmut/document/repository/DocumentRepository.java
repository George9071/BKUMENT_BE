package vn.edu.hcmut.document.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import vn.edu.hcmut.document.entity.Document;

public interface DocumentRepository extends JpaRepository<Document, String> {
    Page<Document> findByTitleContainingIgnoreCase(String keyword, Pageable pageable);

    Page<Document> findByUniversityContainingIgnoreCase(String keyword, Pageable pageable);

    Page<Document> findByCourseContainingIgnoreCase(String keyword, Pageable pageable);
}
