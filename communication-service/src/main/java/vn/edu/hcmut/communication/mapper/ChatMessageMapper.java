package vn.edu.hcmut.communication.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import vn.edu.hcmut.communication.dto.request.ChatMessageRequest;
import vn.edu.hcmut.communication.dto.response.ChatMessageResponse;
import vn.edu.hcmut.communication.entity.ChatMessage;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ChatMessageMapper {
    @Mapping(target = "me", ignore = true)
    ChatMessageResponse toChatMessageResponse(ChatMessage chatMessage);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "sender", ignore = true)
    @Mapping(target = "createdDate", ignore = true)
    ChatMessage toChatMessage(ChatMessageRequest request);

    List<ChatMessageResponse> toChatMessageResponses(List<ChatMessage> chatMessages);
}