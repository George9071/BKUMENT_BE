package vn.edu.hcmut.social.repository;

import java.util.Collection;
import java.util.List;

import jakarta.transaction.Transactional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    @Modifying
    @Transactional
    @Query("UPDATE Report r SET r.deleted = true WHERE r.targetId = :targetId AND r.deleted = false")
    int softDeleteByTargetId(@Param("targetId") String targetId);

    @Query(
            value =
                    """
				SELECT r.targetId               AS targetId,
					COUNT(r)                 AS reportCount,
					MAX(r.createdAt)         AS latestCreatedAt
				FROM Report r
				WHERE r.deleted = false
				AND r.type    = :type
				AND (:status IS NULL OR r.status = :status)
				GROUP BY r.targetId
				ORDER BY MAX(r.createdAt) DESC
				""",
            countQuery =
                    """
				SELECT COUNT(DISTINCT r.targetId)
				FROM Report r
				WHERE r.deleted = false
				AND r.type    = :type
				AND (:status IS NULL OR r.status = :status)
				""")
    Page<Object[]> findGroupedTargets(
            @Param("type") ReportType type, @Param("status") ReportStatus status, Pageable pageable);

    @Query(
            """
		SELECT r FROM Report r
		WHERE r.deleted = false
		AND r.type    = :type
		AND r.targetId IN :targetIds
		AND (:status IS NULL OR r.status = :status)
		ORDER BY r.targetId, r.createdAt DESC
		""")
    List<Report> findReportsForTargets(
            @Param("type") ReportType type,
            @Param("status") ReportStatus status,
            @Param("targetIds") Collection<String> targetIds);

    List<Report> findByTargetIdInAndDeletedFalse(Collection<String> targetIds, Sort sort);

    Page<Report> findByTypeAndDeletedFalse(ReportType type, Pageable pageable);

    Page<Report> findByStatusAndTypeAndDeletedFalse(ReportStatus status, ReportType type, Pageable pageable);

    Page<Report> findByTypeInAndDeletedFalse(Collection<ReportType> types, Pageable pageable);

    Page<Report> findByStatusAndTypeInAndDeletedFalse(
            ReportStatus status, Collection<ReportType> types, Pageable pageable);
}
