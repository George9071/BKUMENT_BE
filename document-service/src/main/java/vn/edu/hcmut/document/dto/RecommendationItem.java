package vn.edu.hcmut.document.dto;

import lombok.*;
import lombok.experimental.FieldDefaults;
import vn.edu.hcmut.document.dto.response.RecommendationReason;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RecommendationItem {
    String docId;
    RecommendationReason reason;
}
