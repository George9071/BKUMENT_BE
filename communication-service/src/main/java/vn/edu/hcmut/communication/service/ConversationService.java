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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
            conversation.setAvatar(request.getConversationAvatar());
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
        String userId = getProfileIdFromToken();

        List<String> participantIds = new ArrayList<>(request.getParticipantIds());
        if (!participantIds.contains(userId)) participantIds.add(userId);

        if ("DIRECT".equals(request.getType())) {
            if (participantIds.size() != 2) throw new AppException(ErrorCode.INVALID_DIRECT_CHAT_MEMBERS);

            String hash = generateParticipantHash(participantIds.stream().sorted().toList());

            var conversation = conversationRepository.findByParticipantsHash(hash);
            if (conversation.isPresent()) return toConversationResponse(conversation.get());

            return createConversation(request, participantIds, hash);
        } else {
            return createConversation(request, participantIds, null);
        }
    }

    private ConversationResponse createConversation(
            ConversationRequest request,
            List<String> participantsIds,
            String hash) {

        List<ParticipantInfo> participantInfos = new ArrayList<>();
        Map<String, Boolean> initialReadStatus = new HashMap<>();

        for (String id : participantsIds) {
            initialReadStatus.put(id, true);
            var profile = profileClient.getProfile(id);
            if (profile == null || profile.getResult() == null) continue;

            var info = profile.getResult();

            participantInfos.add(ParticipantInfo.builder()
                    .userId(info.getId())
                    .username(info.getLastName() + " " + info.getFirstName())
                    .firstName(info.getFirstName())
                    .lastName(info.getLastName())
                    .avatar(info.getAvatarUrl())
                    .build());
        }

        String name = "GROUP".equals(request.getType()) ? request.getName() : null;
        String avatar = "GROUP".equals(request.getType()) ? request.getAvatar() : null;

        Conversation conversation = Conversation.builder()
                .type(request.getType())
                .name(name)
                .avatar(avatar)
                .participantsHash(hash)
                .participantIds(participantsIds)
                .createdDate(Instant.now())
                .modifiedDate(Instant.now())
                .participants(participantInfos)
                .isParticipantRead(initialReadStatus) // Thêm dòng này
                .build();

        return toConversationResponse(conversationRepository.save(conversation));
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

        ConversationResponse response = conversationMapper.toConversationResponse(conversation);

        if (conversation.getIsParticipantRead() != null) {
            Boolean isRead = conversation.getIsParticipantRead().get(userId);
            response.setIsRead(isRead != null ? isRead : true);
        } else {
            response.setIsRead(true); 
        }
        if ("DIRECT".equals(conversation.getType())) {
            conversation.getParticipants().stream()
                    .filter(p -> !p.getUserId().equals(userId))
                    .findFirst()
                    .ifPresent(p -> {
                        response.setConversationName(p.getUsername());
                        response.setConversationAvatar(p.getAvatar());
                    });
        }

        return response;
    }
}
