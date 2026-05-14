package vn.edu.hcmut.lms.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.edu.hcmut.lms.constant.AdminActionType;
import vn.edu.hcmut.lms.entity.AdminActionLog;

import java.time.Instant;

@Repository
public interface AdminActionLogRepository extends JpaRepository<AdminActionLog, String> {

    Page<AdminActionLog> findByActorId(String actorId, Pageable pageable);

    Page<AdminActionLog> findByAction(AdminActionType action, Pageable pageable);

    Page<AdminActionLog> findByTargetTypeAndTargetId(
            String targetType, String targetId, Pageable pageable);

    Page<AdminActionLog> findByCreatedAtBetween(Instant from, Instant to, Pageable pageable);
}
