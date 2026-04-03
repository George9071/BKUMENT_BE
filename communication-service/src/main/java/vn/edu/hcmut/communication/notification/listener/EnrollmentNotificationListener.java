package vn.edu.hcmut.communication.notification.listener;

import com.corundumstudio.socketio.SocketIOServer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import vn.edu.hcmut.communication.notification.dto.response.NotificationResponse;
import vn.edu.hcmut.communication.notification.entity.Notification;
import vn.edu.hcmut.communication.notification.mapper.NotificationMapper;
import vn.edu.hcmut.communication.session.entity.WebSocketSession;
import vn.edu.hcmut.communication.session.repository.WebSocketSessionRepository;
import vn.edu.hcmut.communication.notification.event.EnrollmentNotificationEvent;
import vn.edu.hcmut.communication.notification.repository.NotificationRepository;

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
    NotificationMapper notificationMapper;

    @KafkaListener(topics = "enrollment-events", groupId = "communication-enrollment-group")
    public void handleEnrollmentEvent(EnrollmentNotificationEvent event) {
        log.info("Received: action={}, class={}", event.getAction(), event.getClassId());

        NotificationContent content = resolveContent(event);
        if (content == null) return;

        Notification notification = notificationRepository.save(
                Notification.builder()
                        .recipientId(content.target())
                        .type(content.type())
                        .title(content.title())
                        .message(content.message())
                        .isRead(false)
                        .metadata(Map.of(
                                "classId",   event.getClassId(),
                                "studentId", event.getStudentId(),
                                "action",    event.getAction()))
                        .createdDate(Instant.now())
                        .build());

        var payload = notificationMapper.toResponse(notification);

        sendSocketNotification(content.target(), payload);
    }

    /**
     * Helper function to push notifications via netty-socketio based on Session DB
     */
    private void sendSocketNotification(String userId, NotificationResponse notification) {
        try {
            List<WebSocketSession> sessions = webSocketSessionRepository.findAllByUserIdIn(List.of(userId));

            if (sessions.isEmpty()) {
                log.info("User {} is offline.", userId);
                return;
            }

            String payload = objectMapper.writeValueAsString(notification);

            for (WebSocketSession session : sessions) {
                var client = socketIOServer.getClient(UUID.fromString(session.getSocketSessionId()));
                if (client != null) {
                    client.sendEvent("notification", payload);
                }
            }
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize notification payload", e);
        } catch (Exception e) {
            log.error("Error sending socket notification to user {}", userId, e);
        }
    }

    private NotificationContent resolveContent(EnrollmentNotificationEvent event) {
        return switch (event.getAction()) {
            case "REQUESTED" -> new NotificationContent(
                    event.getTutorId(), "INFO",
                    "Yêu cầu tham gia lớp mới",
                    "Học viên %s muốn tham gia lớp %s."
                            .formatted(event.getStudentName(), event.getClassName()));

            case "APPROVED" -> new NotificationContent(
                    event.getStudentId(), "SUCCESS",
                    "Đã được phê duyệt",
                    "Yêu cầu tham gia lớp %s đã được duyệt."
                            .formatted(event.getClassName()));

            case "REJECTED" -> new NotificationContent(
                    event.getStudentId(), "WARNING",
                    "Từ chối yêu cầu",
                    "Yêu cầu tham gia lớp %s đã bị từ chối."
                            .formatted(event.getClassName()));

            default -> {
                log.warn("Unknown action: {}", event.getAction());
                yield null;
            }
        };
    }

    private record NotificationContent(
            String target, String type, String title, String message) {}
}
