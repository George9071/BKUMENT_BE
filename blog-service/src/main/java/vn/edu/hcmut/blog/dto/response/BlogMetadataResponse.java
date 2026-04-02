package vn.edu.hcmut.blog.dto.response;

import java.time.LocalDateTime;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class BlogMetadataResponse {
    String id;
    String name;
    Author author;
    String coverImage;

    String content;
    LocalDateTime createdAt;
    Long views;

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
