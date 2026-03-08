package vn.edu.hcmut.communication.service;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import vn.edu.hcmut.communication.dto.request.ConversationMetadataRequest;
import vn.edu.hcmut.communication.dto.request.ConversationRequest;
import vn.edu.hcmut.communication.dto.response.ConversationResponse;
import vn.edu.hcmut.communication.entity.Conversation;
import vn.edu.hcmut.communication.entity.ParticipantInfo;
import vn.edu.hcmut.communication.exception.AppException;
import vn.edu.hcmut.communication.exception.ErrorCode;
import vn.edu.hcmut.communication.mapper.ConversationMapper;
import vn.edu.hcmut.communication.repository.ConversationRepository;
import vn.edu.hcmut.communication.repository.httpclient.ProfileClient;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ConversationService {
    ConversationRepository conversationRepository;
    ProfileClient profileClient;

    ConversationMapper conversationMapper;

    public ConversationResponse updateMetadata(String id, ConversationMetadataRequest request) {
        Conversation conversation = conversationRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.CONVERSATION_NOT_FOUND));

        if (request.getType() != null) {
            conversation.setType(request.getType());
        }

        if (request.getName() != null) {
            conversation.setName(request.getName());
        }

        if (request.getConversationAvatar() != null) {
            conversation.setConversationAvatar(request.getConversationAvatar());
        }

        if (request.getLastMessage() != null) {
            conversation.setLastMessage(request.getLastMessage());
        }

        if (request.getLastMessageTime() != null) {
            conversation.setLastMessageTime(request.getLastMessageTime());
        }

        conversation.setModifiedDate(Instant.now());

        return toConversationResponse(conversationRepository.save(conversation));
    }

    public Page<ConversationResponse> myConversations(Pageable pageable) {
        String userId = getProfileIdFromToken();

        List<Conversation> allConversations = conversationRepository.findAllByParticipantIdsContains(userId);

        List<ConversationResponse> pagedResponses = allConversations.stream()
                .sorted((c1, c2) -> {
                    Instant t1 = c1.getLastMessageTime() != null ? c1.getLastMessageTime() : c1.getCreatedDate();
                    Instant t2 = c2.getLastMessageTime() != null ? c2.getLastMessageTime() : c2.getCreatedDate();
                    return t2.compareTo(t1);
                })
                .skip(pageable.getOffset())
                .limit(pageable.getPageSize()) 
                .map(this::toConversationResponse)
                .toList();

        return new PageImpl<>(pagedResponses, pageable, allConversations.size());
    }

    public ConversationResponse create(ConversationRequest request) {
        // Fetch user infos
        String userId = getProfileIdFromToken();
        var user = profileClient.getProfile(userId);

        // Assume only 2 participants
        var participant = profileClient.getProfile(request.getParticipantIds().get(0));

        if (Objects.isNull(user) || Objects.isNull(participant)) {
            throw new AppException(ErrorCode.USER_NOT_FOUND);
        }

        var userInfo = user.getResult();
        var participantInfo = participant.getResult();

        List<String> userIds = new ArrayList<>();
        userIds.add(userId);
        userIds.add(participantInfo.getId());

        var sortedIds = userIds.stream().sorted().toList();
        String userIdsHash = generateParticipantHash(sortedIds);

        var conversation = conversationRepository.findByParticipantsHash(userIdsHash)
                .orElseGet(() -> {
                    List<ParticipantInfo> participantInfos = List.of(
                            // Request sender
                            ParticipantInfo.builder()
                                    .userId(userInfo.getId())
                                    .username(userInfo.getLastName() + " " + userInfo.getFirstName())
                                    .firstName(userInfo.getFirstName())
                                    .lastName(userInfo.getLastName())
                                    .avatar(userInfo.getAvatarUrl())
                                    .build(),

                            // participants
                            ParticipantInfo.builder()
                                    .userId(participantInfo.getId())
                                    .username(participantInfo.getLastName() + " " + participantInfo.getFirstName())
                                    .firstName(participantInfo.getFirstName())
                                    .lastName(participantInfo.getLastName())
                                    .avatar(participantInfo.getAvatarUrl())
                                    .build());

                    // Build conversation info
                    Conversation newConversation = Conversation.builder()
                            .type(request.getType())
                            .participantsHash(userIdsHash)
                            .createdDate(Instant.now())
                            .modifiedDate(Instant.now())
                            .participants(participantInfos)
                            .build();

                    return conversationRepository.save(newConversation);
                });

        return toConversationResponse(conversation);
    }

    private String getProfileIdFromToken() {
        var jwt = (Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return jwt.getClaimAsString("profile_id");
    }

    private String generateParticipantHash(List<String> ids) {
        StringJoiner stringJoiner = new StringJoiner("_");
        ids.forEach(stringJoiner::add);
        return stringJoiner.toString();
    }

    private ConversationResponse toConversationResponse(Conversation conversation) {
        String userId = getProfileIdFromToken();

        ConversationResponse conversationResponse = conversationMapper.toConversationResponse(conversation);

        conversation.getParticipants().stream()
                .filter(participantInfo -> !participantInfo.getUserId().equals(userId))
                .findFirst().ifPresent(participantInfo -> {
                    conversationResponse.setConversationName(participantInfo.getUsername());

                    try {
                        var profile = profileClient.getProfile(participantInfo.getUserId());
                        if (profile != null && profile.getResult() != null) {
                            conversationResponse.setConversationAvatar(profile.getResult().getAvatarUrl());
                        } else {
                            conversationResponse.setConversationAvatar(participantInfo.getAvatar());
                        }
                    } catch (Exception e) {
                        log.warn("Failed to fetch avatar for user {}, falling back to snapshot",
                                participantInfo.getUserId());
                        conversationResponse.setConversationAvatar(participantInfo.getAvatar());
                    }
                });

        return conversationResponse;
    }
}
