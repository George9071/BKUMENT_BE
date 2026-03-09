package vn.edu.hcmut.social.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import vn.edu.hcmut.social.entity.Comment;

@Repository
public interface CommentRepository extends JpaRepository<Comment, String> {
    Page<Comment> findByResourceIdAndReplyIdIsNull(String resourceId, Pageable pageable);

    Page<Comment> findByReplyId(String replyId, Pageable pageable);

    int countByReplyId(String replyId);
}
