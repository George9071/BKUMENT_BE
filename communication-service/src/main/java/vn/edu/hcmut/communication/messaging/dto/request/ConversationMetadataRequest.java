package vn.edu.hcmut.communication.messaging.dto.request;

import lombok.*;
import lombok.experimental.FieldDefaults;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ConversationMetadataRequest {
    String type;
    String name;
    String conversationAvatar;
    String lastMessage;
    Instant lastMessageTime;
}