package vn.edu.hcmut.blog.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import vn.edu.hcmut.blog.entity.Post;

public interface PostRepository extends JpaRepository<Post, String> {
    Page<Post> findByTitleContainingIgnoreCase(String title, Pageable pageable);
}
