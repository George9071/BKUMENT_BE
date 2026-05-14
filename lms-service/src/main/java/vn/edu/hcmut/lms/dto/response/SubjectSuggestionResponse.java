package vn.edu.hcmut.lms.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;
import vn.edu.hcmut.lms.constant.SuggestionStatus;
import vn.edu.hcmut.lms.constant.SuggestionType;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SubjectSuggestionResponse {
    String id;
    String reporterId;
    SuggestionType type;
    String proposedName;
    String parentSubjectId;
    String parentSubjectName;
    String reason;
    SuggestionStatus status;
    String reviewerId;
    String rejectionReason;
    String createdResourceId;
    Instant reviewedAt;
    Instant createdAt;
    Instant updatedAt;
}
