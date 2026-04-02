package vn.edu.hcmut.communication.messaging.mapper;

import org.mapstruct.Mapper;
import vn.edu.hcmut.communication.messaging.dto.response.ConversationResponse;
import vn.edu.hcmut.communication.messaging.entity.Conversation;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ConversationMapper {
    ConversationResponse toConversationResponse(Conversation conversation);

    List<ConversationResponse> toConversationResponseList(List<Conversation> conversations);
}
