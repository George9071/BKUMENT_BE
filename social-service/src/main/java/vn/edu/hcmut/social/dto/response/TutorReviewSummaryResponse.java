package vn.edu.hcmut.social.dto.response;

import java.util.Map;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TutorReviewSummaryResponse {
    String tutorId;
    Double averageScore;
    Long totalReviews;
    Map<Integer, Long> ratingCounts;
}
