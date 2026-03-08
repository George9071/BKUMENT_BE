package vn.edu.hcmut.social.dto.response;

import java.time.LocalDateTime;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CommentResponse {
    String id;
    String replyId;
    String content;
    String resourceId;
    Author author; // Reused pattern from Document Metadata
    LocalDateTime createdAt;
    LocalDateTime updatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class Author {
        String id;
        String name;
        String avatarUrl;
    }
}
