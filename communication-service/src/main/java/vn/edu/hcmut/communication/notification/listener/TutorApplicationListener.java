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
import vn.edu.hcmut.communication.notification.event.TutorApplicationEvent;
import vn.edu.hcmut.communication.notification.mapper.NotificationMapper;
import vn.edu.hcmut.communication.notification.repository.NotificationRepository;
import vn.edu.hcmut.communication.session.entity.WebSocketSession;
import vn.edu.hcmut.communication.session.repository.WebSocketSessionRepository;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class TutorApplicationListener {

    SocketIOServer socketIOServer;
    WebSocketSessionRepository webSocketSessionRepository;
    NotificationRepository notificationRepository;
    ObjectMapper objectMapper;
    NotificationMapper notificationMapper;

    @KafkaListener(
            topics = "tutor-events",
            groupId = "communication-tutor-group",
            properties = "spring.json.value.default.type=vn.edu.hcmut.communication.notification.event.TutorApplicationEvent")
    public void handleApplicationEvent(TutorApplicationEvent event) {
        log.info("Received tutor application event: profile={}, action={}", event.getProfileId(), event.getAction());

        String title;
        String message;
        String type;

        if ("APPROVED".equals(event.getAction())) {
            title = "Đăng ký Gia sư thành công";
            message = "Chúc mừng! Đơn đăng ký trở thành Gia sư của bạn đã được phê duyệt. Bạn có thể bắt đầu tạo lớp học ngay bây giờ.";
            type = "SUCCESS";
        } else if ("REJECTED".equals(event.getAction())) {
            title = "Đơn đăng ký bị từ chối";
            message = "Rất tiếc, đơn đăng ký của bạn chưa được duyệt. Lý do: " + event.getReason();
            type = "WARNING";
        } else {
            return;
        }

        Notification notification = notificationRepository.save(
                Notification.builder()
                        .recipientId(event.getProfileId())
                        .type(type)
                        .title(title)
                        .message(message)
                        .isRead(false)
                        .metadata(Map.of("action", event.getAction()))
                        .createdDate(Instant.now())
                        .build());

        var payload = notificationMapper.toResponse(notification);

        sendSocketNotification(event.getProfileId(), payload);
    }

    private void sendSocketNotification(String userId, NotificationResponse notification) {
        try {
            List<WebSocketSession> sessions = webSocketSessionRepository.findAllByUserIdIn(List.of(userId));

            if (sessions.isEmpty()) {
                log.info("User {} is offline, notification stored in DB only.", userId);
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
}
