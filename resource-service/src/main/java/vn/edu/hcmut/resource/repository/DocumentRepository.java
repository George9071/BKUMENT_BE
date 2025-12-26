package vn.edu.hcmut.resource.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import vn.edu.hcmut.resource.entity.Document;

public interface DocumentRepository extends JpaRepository<Document, String> {}
