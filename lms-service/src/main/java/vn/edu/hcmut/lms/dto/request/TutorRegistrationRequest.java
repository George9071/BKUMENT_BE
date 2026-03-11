package vn.edu.hcmut.lms.dto.request;

import lombok.*;
import lombok.experimental.FieldDefaults;
import jakarta.validation.constraints.NotBlank;

import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TutorRegistrationRequest {
    @NotBlank(message = "REQUIRED_FIELD")
    String introduction;

    @NotBlank(message = "REQUIRED_FIELD")
    String name;

    String avatar;

    Set<String> subjectIds;
}
