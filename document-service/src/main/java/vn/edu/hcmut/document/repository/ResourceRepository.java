package vn.edu.hcmut.document.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import vn.edu.hcmut.document.entity.Resource;

public interface ResourceRepository extends JpaRepository<Resource, String> {}
