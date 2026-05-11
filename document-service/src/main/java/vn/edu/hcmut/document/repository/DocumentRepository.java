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

/**
 * Index recommendations:
 *   -- For ranking/trending queries
 *   CREATE INDEX idx_resource_created_ranking ON resource (created_at, ranking_score DESC)
 *       WHERE ranking_score IS NOT NULL;
 *
 *   -- For semantic search (pgvector ANN)
 *   CREATE INDEX idx_document_embedding_ivfflat ON document
 *       USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);
 *
 *   -- For full-text search component of findRelatedDocumentIds
 *   CREATE INDEX idx_document_fts ON document
 *       USING GIN (to_tsvector('simple', COALESCE(keywords, '')));
 *   CREATE INDEX idx_resource_title_fts ON resource
 *       USING GIN (to_tsvector('simple', COALESCE(title, '')));
 *
 *   -- For download count increment
 *   CREATE INDEX idx_document_id ON document (id);
 *
 *   -- For owner-based queries
 *   CREATE INDEX idx_resource_owner_id ON resource (owner_id);
 */
public interface DocumentRepository extends JpaRepository<Document, String> {

    Page<Document> findByCourseId(String courseId, Pageable pageable);

    List<Document> findByOwnerId(String ownerId);

    Page<Document> findByOwnerId(String ownerId, Pageable pageable);

    Page<Document> findByTitleContainingIgnoreCase(String keyword, Pageable pageable);

    Page<Document> findByUniversityContainingIgnoreCase(String keyword, Pageable pageable);

    Page<Document> findByCourseContainingIgnoreCase(String keyword, Pageable pageable);

    void deleteByOwnerId(String ownerId);

    /**
     * Atomically increments the view counter for a batch of resources in one UPDATE.
     */
    @Modifying
    @Transactional
    @Query("UPDATE Resource r SET r.views = COALESCE(r.views, 0) + 1 WHERE r.id IN :ids")
    void incrementViews(@Param("ids") List<String> ids);

    /**
     * Atomically increments the download counter for a single document.
     */
    @Modifying
    @Transactional
    @Query("UPDATE Document d SET d.downloadCount = COALESCE(d.downloadCount, 0) + 1 WHERE d.id = :docId")
    void incrementDownloadCount(@Param("docId") String docId);

    /**
     * Hybrid semantic + full-text search ranked by a weighted combination of:
     *   - Vector similarity (weight 0.7): cosine similarity via pgvector <=> operator.
     *     Score = 1 - cosine_distance, range [0, 1], higher = more similar.
     *   - Full-text relevance (weight 0.3): ts_rank_cd over title + keywords tsvector.
     * * * *
     * Performance notes:
     *   - The vector component benefits from an IVFFlat or HNSW index on the embedding
     *     column (see index recommendations in the class Javadoc).
     *   - The ts_rank_cd call applies to_tsvector at query time on every row — this is
     *     acceptable for the result set returned after the vector pre-filter, but if
     *     full-text precision matters more than speed, add a generated tsvector column
     *     with a GIN index and rewrite the query to use @@ for pre-filtering.
     *   - The ORDER BY expression mixes vector and text scores inline; this prevents
     *     index-only sort and always requires a full sort on the candidate set.
     *
     * @param vector    the source document's embedding serialised as "[f1, f2, ...]"
     * @param query     the full-text search query string (plainto_tsquery format)
     * @param excludeId the source document's ID (excluded from results)
     * @param pageable  page and size parameters
     * @return a Page of document ID strings ordered by hybrid score descending
     */
    @Query(value = """
            SELECT d.id FROM document d
            JOIN resource r ON d.id = r.id
            WHERE d.id <> :excludeId
              AND d.embedding IS NOT NULL
            
            ORDER BY (1 - (d.embedding <=> CAST(:vector AS vector))) * 0.7
                               + ts_rank_cd(
                                     to_tsvector('simple',
                                         COALESCE(r.title, '') || ' ' ||
                                         COALESCE(array_to_string(d.keywords, ' '), '')),
                                     plainto_tsquery('simple', :query)
                                 ) * 0.3 DESC
            """,
            countQuery = """
            SELECT COUNT(*) FROM document d
            JOIN resource r ON d.id = r.id
            WHERE d.id <> :excludeId
              AND d.embedding IS NOT NULL
            """,
            nativeQuery = true)
    Page<String> findRelatedDocumentIds(
            @Param("vector") String vector,
            @Param("query") String query,
            @Param("excludeId") String excludeId,
            Pageable pageable);

    /**
     * Returns a paginated list of recent documents ordered by pre-computed rankingScore.
     * Used by:
     *   - DocumentService.getTopRankedDocuments (primary query)
     *   - DocumentRecommendationService.getForYouFeed Layer 3 (trending fallback)
     *   - DocumentRankingScheduler.recalculateRankingScores (chunked iteration)
     * @param since    lower bound for document creation date (e.g. now minus 90 days)
     * @param pageable page number, size, and optional additional sort
     * @return a Page of Documents ordered by rankingScore DESC within the time window
     */
    @Query(value = """
                    SELECT d FROM Document d
                    WHERE d.createdAt >= :since
                    ORDER BY d.rankingScore DESC
                    """,
            countQuery = """
                    SELECT COUNT(d) FROM Document d
                    WHERE d.createdAt >= :since
                    """)
    Page<Document> findRecentDocumentsByRankingScore(
            @Param("since") LocalDateTime since, Pageable pageable);


    /**
     * Counts documents created on or after {@code since}.
     * Used for fallback total computation when pagination metadata is needed without loading entity data.
     * * * *
     * @param since the inclusive lower bound for createdAt
     * @return the number of matching documents
     */
    @Query("SELECT COUNT(d) FROM Document d WHERE d.createdAt >= :since")
    long countRecentDocuments(@Param("since") LocalDateTime since);

    /**
     * Returns all documents ordered by createdAt DESC.
     */
    @Query(value = """
                    SELECT d FROM Document d
                    ORDER BY d.createdAt DESC
                    """,
            countQuery = "SELECT COUNT(d) FROM Document d")
    Page<Document> findAllOrderByCreatedAtDesc(Pageable pageable);

    /**
     * Updates the pre-computed ranking score for a single document.
     * * * *
     * NOTE — individual per-document UPDATE:
     *   For very large catalogues (100k+ documents) this produces N individual UPDATE
     *   statements per scheduler cycle. Consider a bulk UPDATE … CASE … WHEN approach
     *   or batching via JDBC if scheduler execution time becomes a concern.
     *
     * @param id    the resource/document ID to update
     * @param score the newly computed ranking score
     */
    @Modifying
    @Transactional
    @Query("UPDATE Resource r SET r.rankingScore = :score WHERE r.id = :id")
    void updateRankingScore(@Param("id") String id, @Param("score") double score);
}
