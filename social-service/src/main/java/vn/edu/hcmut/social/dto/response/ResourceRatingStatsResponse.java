package vn.edu.hcmut.social.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ResourceRatingStatsResponse {
    String resourceId; // Document ID
    Double averageRating; // R
    Long ratingCount; // v
}
