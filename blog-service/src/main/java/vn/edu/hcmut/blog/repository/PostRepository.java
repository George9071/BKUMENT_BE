package vn.edu.hcmut.blog.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.hcmut.blog.entity.Post;

public interface PostRepository extends JpaRepository<Post, String> {

    Page<Post> findByOwnerId(String ownerId, Pageable pageable);

    @Transactional
    void deleteByOwnerId(String ownerId);

    @Modifying
    @Transactional
    @Query("UPDATE Resource r SET r.views = COALESCE(r.views, 0) + 1 WHERE r.id IN :ids")
    void incrementViews(@Param("ids") List<String> ids);

    /**
     * Full-text search across post title and content using tsvector.
     * * * *
     * Note: search_vector is built from title + content.
     * To search additional fields (e.g. description), update the generated column expression
     * * * *
     * Empty / blank queries should be filtered out at the service layer,
     * because {@code plainto_tsquery('simple', '')} produces an empty tsquery that matches nothing.
     *
     *
     * @param query    user input — passed verbatim to {@code plainto_tsquery}
     * @param pageable page and size
     * @return a Page of Posts ordered by descending relevance.
     */
    @Query(
            value = """
                    SELECT p.*
                    FROM post p
                    JOIN resource r ON p.id = r.id
                    WHERE p.search_vector @@ plainto_tsquery('simple', :query)
                    ORDER BY ts_rank_cd(p.search_vector, plainto_tsquery('simple', :query)) DESC
                    """,
            countQuery = """
                    SELECT COUNT(*)
                    FROM post p
                    WHERE p.search_vector @@ plainto_tsquery('simple', :query)
                    """,
            nativeQuery = true)
    Page<Post> searchByFullText(@Param("query") String query, Pageable pageable);

    /**
     * Returns recent posts ordered by the pre-computed trending_score column.
     * @param since    inclusive lower bound for created_at (e.g. now - 30 days)
     * @param pageable page number and size
     */
    @Query(
            value = """
                    SELECT p FROM Post p
                    WHERE p.createdAt >= :since
                    ORDER BY p.trendingScore DESC
                    """,
            countQuery = """
                    SELECT COUNT(p) FROM Post p
                    WHERE p.createdAt >= :since
                    """)
    Page<Post> findRecentPostsByTrendingScore(
            @Param("since") LocalDateTime since, Pageable pageable);

    @Query(
            value = "SELECT p FROM Post p ORDER BY p.createdAt DESC",
            countQuery = "SELECT COUNT(p) FROM Post p")
    Page<Post> findAllOrderByCreatedAtDesc(Pageable pageable);

    @Modifying
    @Transactional
    @Query("UPDATE Resource r SET r.trendingScore = :score WHERE r.id = :id")
    void updateTrendingScore(@Param("id") String id, @Param("score") double score);

//    @Query("SELECT p FROM Post p JOIN Resource r ON p.id = r.id WHERE r.createdAt >= :since")
//    List<Post> findRecentPostsForScoring(@Param("since") java.time.LocalDateTime since);
}
