package vn.edu.hcmut.document.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import vn.edu.hcmut.document.entity.DocumentDownload;

public interface DocumentDownloadRepository extends JpaRepository<DocumentDownload, String> {}
