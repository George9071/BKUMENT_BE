package vn.edu.hcmut.document.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RecommendationReason {
    String type; // e.g., "DOWNLOADED", "INTERESTED_TOPIC", "ENROLLED_CLASS", "TRENDING"
    String title; // e.g., "Giải tích 1"
}
