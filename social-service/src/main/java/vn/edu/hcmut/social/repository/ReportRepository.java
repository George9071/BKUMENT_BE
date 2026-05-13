package vn.edu.hcmut.social.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import vn.edu.hcmut.social.entity.Report;
import vn.edu.hcmut.social.enums.ReportStatus;
import vn.edu.hcmut.social.enums.ReportType;

@Repository
public interface ReportRepository extends JpaRepository<Report, String> {
    Page<Report> findByDeletedFalse(Pageable pageable);

    Page<Report> findByStatusAndDeletedFalse(ReportStatus status, Pageable pageable);

    boolean existsByReporterIdAndTargetIdAndStatusAndDeletedFalse(
            String reporterId, String targetId, ReportStatus status);

    List<Report> findByTypeAndStatusAndDeletedFalse(ReportType type, ReportStatus status);

    List<Report> findByTargetIdInAndDeletedFalse(List<String> targetIds);
}
