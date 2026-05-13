package vn.edu.hcmut.blog.dto.response;

import java.time.LocalDateTime;
import java.util.List;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ReportedBlogResponse {
    String id;
    String name;
    BlogMetadataResponse.Author author;
    String coverImage;

    String content;
    LocalDateTime createdAt;
    Long views;

    List<ReportInfo> reportList;
}
