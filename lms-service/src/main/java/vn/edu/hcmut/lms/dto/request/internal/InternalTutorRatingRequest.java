package vn.edu.hcmut.lms.dto.request.internal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InternalTutorRatingRequest {
    Double averageRating;
    Integer ratingCount;
}
