package vn.edu.hcmut.lms.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.edu.hcmut.lms.entity.ClassNotification;

@Repository
public interface NotificationRepository extends JpaRepository<ClassNotification, Long>{
    Page<ClassNotification> findByClassRoomIdOrderBySentAtDesc(String classId,
                                                               Pageable pageable);
}
