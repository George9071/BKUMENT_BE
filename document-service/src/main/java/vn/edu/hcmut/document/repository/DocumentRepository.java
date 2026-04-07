package vn.edu.hcmut.document.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.hcmut.document.entity.Document;

public interface DocumentRepository extends JpaRepository<Document, String> {
    @Modifying
    @Transactional
    @Query("UPDATE Resource r SET r.views = COALESCE(r.views, 0) + 1 WHERE r.id IN :ids")
    void incrementViews(@Param("ids") List<String> ids);

    Page<Document> findByTitleContainingIgnoreCase(String keyword, Pageable pageable);

    Page<Document> findByUniversityContainingIgnoreCase(String keyword, Pageable pageable);

    Page<Document> findByCourseContainingIgnoreCase(String keyword, Pageable pageable);

    Page<Document> findByCourseId(String courseId, Pageable pageable);

    Page<Document> findByOwnerId(String ownerId, Pageable pageable);

    // --- Time-Decay Ranking Queries ---

    @Query(
            "SELECT d FROM Document d JOIN Resource r ON d.id = r.id WHERE r.createdAt >= :since ORDER BY r.rankingScore DESC")
    List<Document> findRecentDocumentsOrderByRankingScore(@Param("since") LocalDateTime since, Pageable pageable);

    @Query("SELECT d FROM Document d JOIN Resource r ON d.id = r.id WHERE r.createdAt >= :since")
    List<Document> findRecentDocumentsForScoring(@Param("since") LocalDateTime since);

    @Modifying
    @Transactional
    @Query("UPDATE Resource r SET r.rankingScore = :score WHERE r.id = :id")
    void updateRankingScore(@Param("id") String id, @Param("score") double score);

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
