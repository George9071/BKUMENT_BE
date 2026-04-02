package vn.edu.hcmut.communication.messaging.service;

import com.corundumstudio.socketio.SocketIOServer;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import vn.edu.hcmut.communication.messaging.dto.request.ChatMessageRequest;
import vn.edu.hcmut.communication.messaging.dto.response.ChatMessageResponse;
import vn.edu.hcmut.communication.messaging.entity.ChatMessage;
import vn.edu.hcmut.communication.messaging.entity.ParticipantInfo;
import vn.edu.hcmut.communication.session.entity.WebSocketSession;
import vn.edu.hcmut.communication.exception.AppException;
import vn.edu.hcmut.communication.exception.ErrorCode;
import vn.edu.hcmut.communication.messaging.mapper.ChatMessageMapper;
import vn.edu.hcmut.communication.messaging.repository.ChatMessageRepository;
import vn.edu.hcmut.communication.messaging.repository.ConversationRepository;
import vn.edu.hcmut.communication.session.repository.WebSocketSessionRepository;
import vn.edu.hcmut.communication.httpclient.ProfileClient;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

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

public Page<ChatMessageResponse> getMessages(String conversationId, Pageable pageable) {
        String userId = getProfileIdFromToken();
        var conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new AppException(ErrorCode.CONVERSATION_NOT_FOUND));

        conversation.getParticipants()
                .stream()
                .filter(participantInfo -> userId.equals(participantInfo.getUserId()))
                .findAny()
                .orElseThrow(() -> new AppException(ErrorCode.CONVERSATION_NOT_FOUND));

        if (conversation.getIsParticipantRead() == null) {
            conversation.setIsParticipantRead(new java.util.HashMap<>());
        }

        Boolean isRead = conversation.getIsParticipantRead().get(userId);
        
        if (Boolean.FALSE.equals(isRead) || isRead == null) {
            conversation.getIsParticipantRead().put(userId, true);
            conversationRepository.save(conversation);
        }
        
        Page<ChatMessage> messagesPage = chatMessageRepository
                .findAllByConversationIdOrderByCreatedDateDesc(conversationId, pageable);

        return messagesPage.map(this::toChatMessageResponse);
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

        // Build chat message info
        if (("TEXT".equals(request.getType()) &&
                (request.getMessage() == null || request.getMessage().trim().isEmpty()))
            || ("IMAGE".equals(request.getType()) &&
                (request.getAttachedUrl() == null || request.getAttachedUrl().trim().isEmpty()))) {
            throw new AppException(ErrorCode.INVALID_MESSAGE_PAYLOAD);
        }

        ChatMessage message = chatMessageMapper.toChatMessage(request);

        message.setSender(ParticipantInfo.builder()
                .userId(user.getId())
                .username(user.getLastName() + " " + user.getFirstName())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .avatar(user.getAvatarUrl())
                .build());
        message.setCreatedDate(Instant.now());
        message = chatMessageRepository.save(message);

        if (conversation.getIsParticipantRead() == null) {
            conversation.setIsParticipantRead(new java.util.HashMap<>());
        }
        
        conversation.getParticipants().forEach(participant -> {
            String pId = participant.getUserId();
            conversation.getIsParticipantRead().put(pId, pId.equals(userId));
        });

        conversation.setLastMessage("IMAGE".equalsIgnoreCase(request.getType()) ? "Hình ảnh" : message.getMessage());
        conversation.setLastMessageTime(message.getCreatedDate());
        conversation.setModifiedDate(Instant.now());
        conversationRepository.save(conversation);

        ChatMessageResponse response = chatMessageMapper.toChatMessageResponse(message);
        String payload;

        try {
            // NOT set 'me' field here; let FE handle it.
            payload = objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize message", e);
        }

        List<String> memberIds = conversation.getParticipants().stream().map(ParticipantInfo::getUserId).toList();
        List<WebSocketSession> sessions = webSocketSessionRepository.findAllByUserIdIn(memberIds);

        sessions.forEach(session -> {
            try {
                var client = socketIOServer.getClient(java.util.UUID.fromString(session.getSocketSessionId()));
                if (client != null) {
                    client.sendEvent("message", payload);
                }
            } catch (Exception e) {
                log.warn("Failed to send socket message to session {}", session.getSocketSessionId());
            }
        });

        ChatMessageResponse response2 = toChatMessageResponse(message);
        response2.setTempId(request.getTempId());

        return response2;
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
