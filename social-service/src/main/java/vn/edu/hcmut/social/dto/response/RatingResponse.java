package vn.edu.hcmut.social.dto.response;

import java.time.LocalDateTime;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RatingResponse {
    String id;
    String resourceId;
    String userId;
    Double score;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}
