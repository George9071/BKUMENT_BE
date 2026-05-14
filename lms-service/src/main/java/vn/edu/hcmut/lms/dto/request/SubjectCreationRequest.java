package vn.edu.hcmut.lms.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SubjectCreationRequest {

    @NotBlank(message = "SUBJECT_ID_REQUIRED")
    @Size(min = 2, max = 64, message = "SUBJECT_ID_LENGTH_INVALID")
    @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "INVALID_RESOURCE_ID_FORMAT")
    String id;

    @NotBlank(message = "SUBJECT_NAME_REQUIRED")
    @Size(min = 2, max = 255, message = "SUBJECT_NAME_LENGTH_INVALID")
    String name;

    @Size(max = 2000, message = "NOTE_TOO_LONG")
    String note;
}
