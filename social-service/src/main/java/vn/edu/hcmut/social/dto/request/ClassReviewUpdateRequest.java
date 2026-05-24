package vn.edu.hcmut.social.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ClassReviewUpdateRequest {
    String comment;

    @Min(value = 1, message = "Score must be at least 1")
    @Max(value = 5, message = "Score cannot be more than 5")
    Double score;
}
