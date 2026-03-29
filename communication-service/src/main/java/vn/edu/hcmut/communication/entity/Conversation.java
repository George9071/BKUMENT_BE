package vn.edu.hcmut.communication.entity;

import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.MongoId;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    String avatar;

    @Indexed(unique = true, partialFilter = "{ type: 'DIRECT' }")
    String participantsHash;

    @Indexed
    List<String> participantIds;

    List<ParticipantInfo> participants;

    String lastMessage;

    @Indexed
    Instant lastMessageTime;

    @Builder.Default
    Map<String, Boolean> isParticipantRead = new HashMap<>();

    Instant createdDate;
    Instant modifiedDate;
}
