package vn.edu.hcmut.lms.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;
import vn.edu.hcmut.lms.constant.AdminActionType;

import java.time.Instant;
import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AdminActionLogResponse {
    String id;
    String actorId;
    String actorRole;
    AdminActionType action;
    String targetType;
    String targetId;
    String sourceSuggestionId;
    Map<String, Object> beforeSnapshot;
    Map<String, Object> afterSnapshot;
    String note;
    Instant createdAt;
}
