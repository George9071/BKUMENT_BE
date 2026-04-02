package vn.edu.hcmut.communication.notification.service;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import vn.edu.hcmut.communication.notification.dto.response.NotificationResponse;
import vn.edu.hcmut.communication.dto.response.PageResponse;
import vn.edu.hcmut.communication.notification.entity.Notification;
import vn.edu.hcmut.communication.exception.AppException;
import vn.edu.hcmut.communication.exception.ErrorCode;
import vn.edu.hcmut.communication.notification.repository.NotificationRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class NotificationService {

    NotificationRepository notificationRepository;

    public PageResponse<NotificationResponse> getMyNotifications(String userId, int page, int size) {
        Pageable pageable = PageRequest.of((page > 0) ? page - 1 : 0, size);

        Page<Notification> notificationPage = notificationRepository.findByRecipientIdOrderByCreatedDateDesc(userId, pageable);

        List<NotificationResponse> responses = notificationPage.getContent().stream()
                .map(this::toResponse)
                .toList();

        return PageResponse.<NotificationResponse>builder()
                .currentPage(page)
                .totalPages(notificationPage.getTotalPages())
                .pageSize(size)
                .totalElements(notificationPage.getTotalElements())
                .data(responses)
                .build();
    }

    public long getUnreadCount(String userId) {
        return notificationRepository.countByRecipientIdAndIsReadFalse(userId);
    }

    public void markAsRead(String notificationId, String userId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new AppException(ErrorCode.NOTIFICATION_NOT_FOUND));

        if (!notification.getRecipientId().equals(userId)) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        if (!notification.isRead()) {
            notification.setRead(true);
            notificationRepository.save(notification);
        }
    }

    public void markAllAsRead(String userId) {
        List<Notification> unreadNotifications = notificationRepository.findByRecipientIdAndIsReadFalse(userId);

        if (!unreadNotifications.isEmpty()) {
            unreadNotifications.forEach(n -> n.setRead(true));
            notificationRepository.saveAll(unreadNotifications);
        }
    }

    private NotificationResponse toResponse(Notification notification) {
        return NotificationResponse.builder()
                .id(notification.getId())
                .type(notification.getType())
                .title(notification.getTitle())
                .message(notification.getMessage())
                .isRead(notification.isRead())
                .metadata(notification.getMetadata())
                .timestamp(notification.getCreatedDate())
                .build();
    }
}
