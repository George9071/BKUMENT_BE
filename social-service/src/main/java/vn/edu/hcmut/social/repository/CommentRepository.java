package vn.edu.hcmut.social.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmut.social.entity.Comment;

@Repository
public interface CommentRepository extends JpaRepository<Comment, String> {

    Page<Comment> findByResourceIdAndReplyIdIsNull(String resourceId, Pageable pageable);

    Page<Comment> findByReplyId(String replyId, Pageable pageable);

    int countByReplyId(String replyId);

    @Query("SELECT c.resourceId, COUNT(c) FROM Comment c GROUP BY c.resourceId")
    List<Object[]> countCommentsGroupByResourceId();

    @Query("""
            SELECT c.replyId, COUNT(c)
            FROM Comment c
            WHERE c.replyId IN :ids
            GROUP BY c.replyId
            """)
    List<Object[]> countRepliesGroupedByParentId(@Param("ids") List<String> parentIds);

    @Modifying
    @Transactional
    @Query("DELETE FROM Comment c WHERE c.replyId = :parentId")
    void deleteByReplyId(@Param("parentId") String parentId);

     @Modifying
     @Transactional
     @Query("DELETE FROM Comment c WHERE c.resourceId = :resourceId")
     int deleteByResourceId(@Param("resourceId") String resourceId);

//    void deleteByResourceId(String resourceId);
}
