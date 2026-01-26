package vn.edu.hcmut.blog.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import vn.edu.hcmut.blog.entity.Resource;

public interface ResourceRepository extends JpaRepository<Resource, String> {}
