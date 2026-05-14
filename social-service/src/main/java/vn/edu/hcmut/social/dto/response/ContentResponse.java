package vn.edu.hcmut.social.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 1 row in the moderation dashboard's reported-content listing.
 * * * *
 * Represents a single piece of content (a blog post or a document) together with the reports filed against it.
 * The same content may have multiple reports from different users, all bundled here so the moderator can decide once.
 * * * *
 * Cross-service fields:
 *   - id, name, cover_image, content, created_at, views, author
 *   -> resolved from the owning service (blog-service or document-service) plus profile-service.
 *   - reportCount, reportList -> from social-service's local reports table.
 * * * *
 * `content` is the truncated preview, not the full HTML body — keeps the payload compact for list views.
 */

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ContentResponse {
    String id;
    String name;
    AuthorResponse author;
    String coverImage;
    String content;
    LocalDateTime createdAt;
    Long views;
    Integer reportCount;
    List<ReportSummary> reportList;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class ReportSummary {
        String reporter;
        String reason;
        LocalDateTime createdAt;
    }
}
