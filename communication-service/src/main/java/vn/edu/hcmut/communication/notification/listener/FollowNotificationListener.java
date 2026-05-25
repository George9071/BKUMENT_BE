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
import vn.edu.hcmut.communication.notification.event.FollowNotificationEvent;
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
public class FollowNotificationListener {
    SocketIOServer socketIOServer;
    WebSocketSessionRepository webSocketSessionRepository;
    NotificationRepository notificationRepository;
    ObjectMapper objectMapper;
    NotificationMapper notificationMapper;

    @KafkaListener(
            topics = "follow-events",
            groupId = "communication-follow-group",
            properties = "spring.json.value.default.type=vn.edu.hcmut.communication.notification.event.FollowNotificationEvent")
    public void handleFollowEvent(FollowNotificationEvent event) {
        log.info("Received follow event: {} followed {}", event.getFollowerId(), event.getFolloweeId());

        if (!"FOLLOWED".equals(event.getAction())) return;

        Notification notification = notificationRepository.save(
                Notification.builder()
                        .recipientId(event.getFolloweeId())
                        .type("INFO")
                        .title("Người theo dõi mới")
                        .message(event.getFollowerName() + " đã bắt đầu theo dõi bạn.")
                        .isRead(false)
                        .metadata(Map.of(
                                "followerId", event.getFollowerId(),
                                "action", event.getAction()))
                        .createdDate(Instant.now())
                        .build());

        var payload = notificationMapper.toResponse(notification);

        // 3. Gửi thông báo qua Socket
        sendSocketNotification(event.getFolloweeId(), payload);
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
