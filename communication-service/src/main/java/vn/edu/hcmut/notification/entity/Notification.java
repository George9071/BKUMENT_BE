package vn.edu.hcmut.notification.entity;

import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.MongoId;

import java.time.Instant;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "notification")
@CompoundIndexes({
    @CompoundIndex(name = "recipient_date_idx", def = "{'recipientId': 1, 'createdDate': -1}"),
    @CompoundIndex(name = "recipient_status_idx", def = "{'recipientId': 1, 'isRead': 1}")
})
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Notification {
    @MongoId
    String id;

    String recipientId;

    String type;

    String title;

    String message;

    @Builder.Default
    boolean isRead = false;

    Map<String, Object> metadata;

    Instant createdDate;
}
