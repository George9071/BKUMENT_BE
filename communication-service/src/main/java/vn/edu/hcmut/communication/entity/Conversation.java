package vn.edu.hcmut.communication.entity;

import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.MongoId;

import java.time.Instant;
import java.util.List;

@Setter
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "conversation")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Conversation {
    @MongoId
    String id;

    String type; // GROUP, DIRECT

    String name;

    @Indexed(unique = true)
    String participantsHash;

    String conversationAvatar;

    List<ParticipantInfo> participants;

    Instant createdDate;
    
    String lastMessage;
    Instant lastMessageTime;

    Instant modifiedDate;
}
