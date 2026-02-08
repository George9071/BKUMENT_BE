package vn.edu.hcmut.document.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import vn.edu.hcmut.document.entity.Document;

public interface DocumentRepository extends JpaRepository<Document, String> {
    Page<Document> findByTitleContainingIgnoreCase(String keyword, Pageable pageable);

    Page<Document> findByUniversityContainingIgnoreCase(String keyword, Pageable pageable);

    Page<Document> findByCourseContainingIgnoreCase(String keyword, Pageable pageable);

    @org.springframework.data.jpa.repository.Query(
            value =
                    """
				SELECT d.id
				FROM document d
				JOIN resource r ON d.id = r.id
				WHERE d.id != :excludeDocId
				AND d.embedding IS NOT NULL
				ORDER BY (
				(1 - (d.embedding <=> CAST(:vector AS vector))) * 0.7 +
				ts_rank_cd(
					to_tsvector('simple', COALESCE(r.title, '') || ' ' || COALESCE(d.keywords, '')),
					plainto_tsquery('simple', :query)
				) * 0.3
				) DESC
			""",
            nativeQuery = true)
    Page<String> findRelatedDocumentIds(
            @org.springframework.data.repository.query.Param("vector") String vector,
            @org.springframework.data.repository.query.Param("query") String query,
            @org.springframework.data.repository.query.Param("excludeDocId") String excludeDocId,
            Pageable pageable);
}
