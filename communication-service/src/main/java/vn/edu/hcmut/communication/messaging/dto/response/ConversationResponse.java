package vn.edu.hcmut.communication.messaging.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;
import vn.edu.hcmut.communication.messaging.entity.ParticipantInfo;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ConversationResponse {
    String id;
    String type; // GROUP, DIRECT
    String participantsHash;
    Boolean isRead;
    String conversationAvatar;
    String conversationName;
    String lastMessage;
    List<ParticipantInfo> participants;
    Instant lastMessageTime;
    Instant createdDate;
    Instant modifiedDate;
}
