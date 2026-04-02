package vn.edu.hcmut.communication.messaging.dto.response;

import vn.edu.hcmut.communication.messaging.entity.ParticipantInfo;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ChatMessageResponse {
    String id;
    String tempId;
    String conversationId;
    boolean me;
    String type;
    String message;
    ParticipantInfo sender;
    Instant createdDate;
}
