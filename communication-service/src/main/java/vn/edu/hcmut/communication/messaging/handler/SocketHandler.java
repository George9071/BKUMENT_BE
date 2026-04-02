package vn.edu.hcmut.communication.messaging.handler;

import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIOServer;
import com.corundumstudio.socketio.annotation.OnConnect;
import com.corundumstudio.socketio.annotation.OnDisconnect;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import vn.edu.hcmut.communication.messaging.dto.request.IntrospectRequest;
import vn.edu.hcmut.communication.session.entity.WebSocketSession;
import vn.edu.hcmut.communication.messaging.service.IdentityService;
import vn.edu.hcmut.communication.session.service.WebSocketSessionService;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SocketHandler {
    SocketIOServer server;
    IdentityService identityService;
    WebSocketSessionService webSocketSessionService;

    @OnConnect
    public void clientConnected(SocketIOClient client) {
        // Get token from request param
        String token = client.getHandshakeData().getSingleUrlParam("token");

        // Verify token
        var introspect = identityService.introspect(IntrospectRequest.builder()
                .token(token)
                .build());

        // If invalid --> disconnect
        if (introspect.isValid()) {
            log.info("Client connected: {}", client.getSessionId());

            // Establish web socket session
            WebSocketSession session = WebSocketSession.builder()
                    .socketSessionId(client.getSessionId().toString())
                    .userId(introspect.getProfileId())
                    .createdAt(Instant.now())
                    .build();

            session = webSocketSessionService.create(session);

            log.info("Web socket session created with id: {}", session.getId());
        } else {
            log.error("Authentication fail: {}", client.getSessionId());
            client.disconnect();
        }
    }

    @OnDisconnect
    public void clientDisconnected(SocketIOClient client) {
        log.info("Client disconnected: {}", client.getSessionId());
        webSocketSessionService.deleteSession(client.getSessionId().toString());
    }

    @PostConstruct
    public void startServer() {
        server.start();
        server.addListeners(this);
        log.info("Socket server started");
    }

    @PreDestroy
    public void stopServer() {
        server.stop();
        log.info("Socket server stoped");
    }
}
