package vn.edu.hcmut.social.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TutorReviewRequest {
    @NotBlank(message = "Tutor ID cannot be blank")
    String tutorId;

    String comment;

    @NotNull(message = "Score cannot be null")
    @Min(value = 1, message = "Score must be at least 1")
    @Max(value = 5, message = "Score cannot be more than 5")
    Double score;
}
