package vn.edu.hcmut.lms.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TutorResponse {
    String id; // Chính là userId
    String introduction;
    Double averageRating;
    Integer ratingCount;
    String status;

    String name;
    String avatar;
}
