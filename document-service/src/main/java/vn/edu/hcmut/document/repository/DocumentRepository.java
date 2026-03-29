package vn.edu.hcmut.document.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import vn.edu.hcmut.document.entity.Document;

public interface DocumentRepository extends JpaRepository<Document, String> {
    Page<Document> findByTitleContainingIgnoreCase(String keyword, Pageable pageable);

    Page<Document> findByUniversityContainingIgnoreCase(String keyword, Pageable pageable);

    Page<Document> findByCourseContainingIgnoreCase(String keyword, Pageable pageable);

    Page<Document> findByCourseId(String courseId, Pageable pageable);

    @Query(
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
            countQuery =
                    """
				SELECT COUNT(d.id)
				FROM document d
				JOIN resource r ON d.id = r.id
				WHERE d.id != :excludeDocId
				AND d.embedding IS NOT NULL
			""",
            nativeQuery = true)
    Page<String> findRelatedDocumentIds(
            @Param("vector") String vector,
            @Param("query") String query,
            @Param("excludeDocId") String excludeDocId,
            Pageable pageable);
}
