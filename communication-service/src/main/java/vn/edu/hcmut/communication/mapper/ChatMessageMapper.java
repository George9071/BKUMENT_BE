package vn.edu.hcmut.communication.mapper;

import org.mapstruct.Mapper;
import vn.edu.hcmut.communication.dto.request.ChatMessageRequest;
import vn.edu.hcmut.communication.dto.response.ChatMessageResponse;
import vn.edu.hcmut.communication.entity.ChatMessage;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ChatMessageMapper {
    ChatMessageResponse toChatMessageResponse(ChatMessage chatMessage);

    ChatMessage toChatMessage(ChatMessageRequest request);

    List<ChatMessageResponse> toChatMessageResponses(List<ChatMessage> chatMessages);
}
