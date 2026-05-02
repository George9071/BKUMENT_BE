package vn.edu.hcmut.communication.messaging.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import vn.edu.hcmut.communication.messaging.dto.response.ConversationResponse;
import vn.edu.hcmut.communication.messaging.entity.Conversation;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ConversationMapper {
    @Mapping(source = "name", target = "conversationName")
    @Mapping(source = "avatar", target = "conversationAvatar")
    ConversationResponse toConversationResponse(Conversation conversation);

    List<ConversationResponse> toConversationResponseList(List<Conversation> conversations);
}
