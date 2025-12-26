package vn.edu.hcmut.resource.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import vn.edu.hcmut.resource.entity.Resource;

public interface ResourceRepository extends JpaRepository<Resource, String> {}
