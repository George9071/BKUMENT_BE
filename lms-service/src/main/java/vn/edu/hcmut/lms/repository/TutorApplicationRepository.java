package vn.edu.hcmut.lms.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.edu.hcmut.lms.constant.ApplicationStatus;
import vn.edu.hcmut.lms.entity.TutorApplication;

import java.util.Optional;

@Repository
public interface TutorApplicationRepository extends JpaRepository<TutorApplication, String> {
    Optional<TutorApplication> findByProfileId(String profileId);
    Page<TutorApplication> findByStatus(ApplicationStatus status, Pageable pageable);}
