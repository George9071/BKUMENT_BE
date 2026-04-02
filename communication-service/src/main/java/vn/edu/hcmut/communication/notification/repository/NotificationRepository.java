package vn.edu.hcmut.communication.notification.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import vn.edu.hcmut.communication.notification.entity.Notification;

import java.util.List;

@Repository
public interface NotificationRepository extends MongoRepository<Notification, String> {
    Page<Notification> findByRecipientIdOrderByCreatedDateDesc(String recipientId, Pageable pageable);

    long countByRecipientIdAndIsReadFalse(String recipientId);

    List<Notification> findByRecipientIdAndIsReadFalse(String recipientId);
}
