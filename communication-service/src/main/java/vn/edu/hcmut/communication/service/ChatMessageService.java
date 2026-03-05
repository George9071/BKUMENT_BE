package vn.edu.hcmut.communication.service;

import com.corundumstudio.socketio.SocketIOServer;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import vn.edu.hcmut.communication.dto.request.ChatMessageRequest;
import vn.edu.hcmut.communication.dto.response.ChatMessageResponse;
import vn.edu.hcmut.communication.entity.ChatMessage;
import vn.edu.hcmut.communication.entity.Conversation;
import vn.edu.hcmut.communication.entity.ParticipantInfo;
import vn.edu.hcmut.communication.entity.WebSocketSession;
import vn.edu.hcmut.communication.exception.AppException;
import vn.edu.hcmut.communication.exception.ErrorCode;
import vn.edu.hcmut.communication.mapper.ChatMessageMapper;
import vn.edu.hcmut.communication.repository.ChatMessageRepository;
import vn.edu.hcmut.communication.repository.ConversationRepository;
import vn.edu.hcmut.communication.repository.WebSocketSessionRepository;
import vn.edu.hcmut.communication.repository.httpclient.ProfileClient;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ChatMessageService {
    SocketIOServer socketIOServer;

    ChatMessageRepository chatMessageRepository;
    ConversationRepository conversationRepository;
    WebSocketSessionRepository webSocketSessionRepository;

    ProfileClient profileClient;

    ObjectMapper objectMapper;
    ChatMessageMapper chatMessageMapper;

    public List<ChatMessageResponse> getMessages(String conversationId) {
        // Validate conversationId
        String userId = getProfileIdFromToken();
        conversationRepository.findById(conversationId)
                .orElseThrow(() -> new AppException(ErrorCode.CONVERSATION_NOT_FOUND))
                .getParticipants()
                .stream()
                .filter(participantInfo -> userId.equals(participantInfo.getUserId()))
                .findAny()
                .orElseThrow(() -> new AppException(ErrorCode.CONVERSATION_NOT_FOUND));

        var messages = chatMessageRepository.findAllByConversationIdOrderByCreatedDateDesc(conversationId);

        return messages.stream().map(this::toChatMessageResponse).toList();
    }


    /**
     * Handles the flow of creating new messages in a conversation
     * and broadcasting those messages to online members via Socket.IO
     * @param  request DTO contains input information (conversationId, message content,...)
     * @return ChatMessageResponse DTO returns the original HTTP request from the sender.
     */
    public ChatMessageResponse create(ChatMessageRequest request) {
        String userId = getProfileIdFromToken();

        // Validate conversationId
        var conversation = conversationRepository.findById(request.getConversationId())
                .orElseThrow(() -> new AppException(ErrorCode.CONVERSATION_NOT_FOUND));

        // Ensure the current user is actually a member of this conversation.
        conversation.getParticipants()
                .stream()
                .filter(participantInfo -> userId.equals(participantInfo.getUserId()))
                .findAny()
                .orElseThrow(() -> new AppException(ErrorCode.CONVERSATION_NOT_FOUND));

        // Get user info from ProfileService
        var user = profileClient.getProfile(userId);

        if (Objects.isNull(user)) {
            throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION);
        }
        var userInfo = user.getResult();

        // Build chat message info
        ChatMessage chatMessage = chatMessageMapper.toChatMessage(request);
        chatMessage.setSender(ParticipantInfo.builder()
                .userId(userInfo.getId())
                .username(userInfo.getLastName() + " " + userInfo.getFirstName())
                .firstName(userInfo.getFirstName())
                .lastName(userInfo.getLastName())
                .avatar(userInfo.getAvatarUrl())
                .build());
        chatMessage.setCreatedDate(Instant.now());

        // Create chat message
        chatMessage = chatMessageRepository.save(chatMessage);

        conversation.setLastMessage(
            "image".equals(request.getType())
                ? "Hình ảnh"
                : chatMessage.getMessage()
        );
        conversation.setLastMessageTime(chatMessage.getCreatedDate());
        conversation.setModifiedDate(Instant.now());

        conversationRepository.save(conversation);

        // Publish socket event to clients in conversation
        // Get participants ids
        List<String> ids = conversation.getParticipants().stream()
                .map(ParticipantInfo::getUserId).toList();

        // Retrieve a list of active WebSocket Sessions for each member (socketSessionId, WebSocketSession).
        Map<String, WebSocketSession> sessions = webSocketSessionRepository
                .findAllByUserIdIn(ids)
                .stream()
                .collect(Collectors.toMap(WebSocketSession::getSocketSessionId, Function.identity()));

        ChatMessageResponse messageResponse = chatMessageMapper.toChatMessageResponse(chatMessage);

        // Browse through all clients currently connected to the socket server.
        socketIOServer.getAllClients().forEach(client -> {

            // Check if the current client is included in the set of participants in the conversation.
            var webSocketSession = sessions.get(client.getSessionId().toString());

            if (Objects.nonNull(webSocketSession)) {
                String message = null;
                try {
                    messageResponse.setMe(webSocketSession.getUserId().equals(userId));
                    message = objectMapper.writeValueAsString(messageResponse);
                    client.sendEvent("message", message);
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                }
            }
        });

        // Convert to response
        return toChatMessageResponse(chatMessage);
    }

    private ChatMessageResponse toChatMessageResponse(ChatMessage chatMessage) {
        String userId = getProfileIdFromToken();

        var response = chatMessageMapper.toChatMessageResponse(chatMessage);
        response.setMe(userId.equals(chatMessage.getSender().getUserId()));

        return response;
    }

    // HELPER METHOD
    private String getProfileIdFromToken() {
        var jwt = (Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return jwt.getClaimAsString("profile_id");
    }
}
