package vn.edu.hcmut.notification.listener;

import com.corundumstudio.socketio.SocketIOServer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import vn.edu.hcmut.notification.dto.response.NotificationResponse;
import vn.edu.hcmut.notification.entity.Notification;
import vn.edu.hcmut.communication.entity.WebSocketSession;
import vn.edu.hcmut.communication.repository.WebSocketSessionRepository;
import vn.edu.hcmut.notification.event.EnrollmentNotificationEvent;
import vn.edu.hcmut.notification.repository.NotificationRepository;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class EnrollmentNotificationListener {
    SocketIOServer socketIOServer;
    WebSocketSessionRepository webSocketSessionRepository;
    NotificationRepository notificationRepository;
    ObjectMapper objectMapper;

    @KafkaListener(topics = "notification-events", groupId = "communication-group")
    public void handleEnrollmentEvent(EnrollmentNotificationEvent event) {
        log.info("Kafka received enrollment event: Action={}, Class={}, Student={}",
                event.getAction(), event.getClassName(), event.getStudentId());

        String target;
        String type;
        String title;
        String message;

        switch (event.getAction()) {
            case "REQUESTED":
                target = event.getTutorId();
                type = "INFO";
                title = "Yêu cầu tham gia lớp mới";
                message = String.format("Học viên %s muốn tham gia lớp %s.",
                        event.getStudentName(),
                        event.getClassName());
                break;
            case "APPROVED":
                target = event.getStudentId();
                type = "SUCCESS";
                title = "Đã được duyệt";
                message = String.format("Yêu cầu tham gia lớp %s đã được duyệt.", event.getClassName());
                break;
            case "REJECTED":
                target = event.getStudentId();
                type = "WARNING";
                title = "Từ chối yêu cầu";
                message = String.format("Yêu cầu tham gia lớp %s đã bị từ chối.", event.getClassName());
                break;
            default:
                log.warn("Unknown enrollment action: {}", event.getAction());
                return;
        }

        Notification notification = Notification.builder()
                .recipientId(target)
                .type(type)
                .title(title)
                .message(message)
                .isRead(false)
                .metadata(Map.of(
                        "classId", event.getClassId(),
                        "studentId", event.getStudentId(),
                        "action", event.getAction()
                ))
                .createdDate(Instant.now())
                .build();

        notification = notificationRepository.save(notification);

        NotificationResponse payload = NotificationResponse.builder()
                .id(notification.getId())
                .type(notification.getType())
                .title(notification.getTitle())
                .message(notification.getMessage())
                .metadata(notification.getMetadata())
                .isRead(notification.isRead())
                .timestamp(notification.getCreatedDate())
                .build();

        sendSocketNotification(target, payload);
    }

    /**
     * Helper function to push notifications via netty-socketio based on Session DB
     */
    private void sendSocketNotification(String userId, NotificationResponse notification) {
        try {
            // Convert the Object to a JSON String
            String jsonPayload = objectMapper.writeValueAsString(notification);

            List<WebSocketSession> sessions = webSocketSessionRepository.findAllByUserIdIn(List.of(userId));

            if (sessions.isEmpty()) {
                log.info("User {} is offline.", userId);
                return;
            }

            for (WebSocketSession session : sessions) {
                var client = socketIOServer.getClient(UUID.fromString(session.getSocketSessionId()));
                if (client != null) {
                    client.sendEvent("notification", jsonPayload);
                }
            }
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize notification payload", e);
        } catch (Exception e) {
            log.error("Error sending socket notification to user {}", userId, e);
        }
    }
}
