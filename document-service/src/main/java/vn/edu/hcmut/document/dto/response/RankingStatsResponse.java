package vn.edu.hcmut.document.dto.response;

import java.util.List;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RankingStatsResponse {
    Double globalAverage;
    List<ResourceRatingStatsResponse> stats;
}
