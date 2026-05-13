package vn.edu.hcmut.blog.dto.response;

import java.time.LocalDateTime;

import lombok.*;
import lombok.experimental.FieldDefaults;

/**
 * Mirrors social-service ReportResponse for Feign deserialization.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SocialReportResponse {
    String id;
    String resolverId;
    String reporterId;
    String targetId;
    String status;
    String type;
    String reason;
    String detail;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}
