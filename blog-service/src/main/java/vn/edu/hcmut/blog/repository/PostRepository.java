package vn.edu.hcmut.blog.repository;

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
    Page<Post> findByTitleContainingIgnoreCase(String title, Pageable pageable);

    Page<Post> findByOwnerId(String ownerId, Pageable pageable);

    @Modifying
    @Transactional
    @Query("UPDATE Resource r SET r.views = COALESCE(r.views, 0) + 1 WHERE r.id IN :ids")
    void incrementViews(@Param("ids") java.util.List<String> ids);

    @Query(
            "SELECT p FROM Post p JOIN Resource r ON p.id = r.id WHERE r.createdAt >= :since ORDER BY r.trendingScore DESC")
    List<Post> findRecentPostsOrderByTrendingScore(@Param("since") java.time.LocalDateTime since, Pageable pageable);

    @Query("SELECT p FROM Post p JOIN Resource r ON p.id = r.id WHERE r.createdAt >= :since")
    List<Post> findRecentPostsForScoring(@Param("since") java.time.LocalDateTime since);

    @Modifying
    @Transactional
    @Query("UPDATE Resource r SET r.trendingScore = :score WHERE r.id = :id")
    void updateTrendingScore(@Param("id") String id, @Param("score") double score);
}
