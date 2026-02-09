package vn.edu.hcmut.lms.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.edu.hcmut.lms.entity.ClassNotification;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<ClassNotification, Long>{
    List<ClassNotification> findByClassRoomIdOrderBySentAtDesc(String classId);
}
