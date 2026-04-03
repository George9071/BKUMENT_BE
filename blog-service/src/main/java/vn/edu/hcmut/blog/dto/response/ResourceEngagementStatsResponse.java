package vn.edu.hcmut.blog.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ResourceEngagementStatsResponse {
    String resourceId;
    Double averageRating;
    Long ratingCount;
    Long commentCount;
}
