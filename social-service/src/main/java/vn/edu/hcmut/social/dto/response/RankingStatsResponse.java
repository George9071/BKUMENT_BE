package vn.edu.hcmut.social.dto.response;

import java.util.List;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RankingStatsResponse {
    Double globalAverage; // C
    List<ResourceRatingStatsResponse> stats;
}
