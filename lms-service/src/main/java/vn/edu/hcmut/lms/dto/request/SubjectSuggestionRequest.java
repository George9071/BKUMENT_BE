package vn.edu.hcmut.lms.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;
import vn.edu.hcmut.lms.constant.SuggestionType;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SubjectSuggestionRequest {

    @NotNull(message = "SUGGESTION_TYPE_REQUIRED")
    SuggestionType type;

    @NotBlank(message = "PROPOSED_NAME_REQUIRED")
    @Size(min = 2, max = 255, message = "PROPOSED_NAME_LENGTH_INVALID")
    String proposedName;

    /**
     * When type = TOPIC, it is mandatory to point to an existing SUBJECT.
     * Ignored when type = SUBJECT.
     */
    String parentSubjectId;

    /** Optional */
    @Size(max = 2000, message = "REASON_TOO_LONG")
    String reason;
}
