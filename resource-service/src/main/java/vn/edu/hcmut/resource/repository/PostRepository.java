package vn.edu.hcmut.resource.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import vn.edu.hcmut.resource.entity.Post;

public interface PostRepository extends JpaRepository<Post, String> {}
