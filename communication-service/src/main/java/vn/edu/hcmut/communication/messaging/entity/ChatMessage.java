package vn.edu.hcmut.communication.messaging.entity;

import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.MongoId;

import java.time.Instant;

@Setter
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "chat_message")
@CompoundIndex(name = "conv_date_idx", def = "{'conversationId': 1, 'createdDate': -1}")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ChatMessage {
    @MongoId
    String id;

    @Indexed
    String conversationId;

    String type; // TEXT, IMAGE, FILE

    String attachedUrl;

    String message;

    ParticipantInfo sender;

    Instant createdDate;
}
