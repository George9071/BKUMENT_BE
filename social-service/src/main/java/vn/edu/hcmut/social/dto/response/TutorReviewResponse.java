package vn.edu.hcmut.social.dto.response;

import java.time.LocalDateTime;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TutorReviewResponse {
    String id;
    String userId;
    String tutorId;
    String comment;
    Double score;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}
