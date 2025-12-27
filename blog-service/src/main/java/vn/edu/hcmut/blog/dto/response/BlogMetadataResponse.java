package vn.edu.hcmut.blog.dto.response;

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
    String authorId;
    String coverImage;

    String content;
}
