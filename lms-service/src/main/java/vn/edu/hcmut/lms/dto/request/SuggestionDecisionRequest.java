package vn.edu.hcmut.lms.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;

/**
 * Body for the two actions: approve and reject the proposal.
 * APPROVE:
 *   - final_name             can be different from proposed_name if the admin wants to standardize spelling.
 *                            If null, use proposed_name itself.
 *   - parent_subject_id     (only applies to TOPIC) allows the admin to attach the topic
 *                            to a subject different from the one the user initially proposed
 *                            If null, keep the original parent subject
 *   - note                   internal note, will be saved to AdminActionLog.
 * * * *
 * REJECT:
 *   - rejection_reason     the reason for rejection displayed to the user in "my proposals".
 *   - note                 internal note
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SuggestionDecisionRequest {

    @Size(min = 2, max = 64, message = "FINAL_ID_LENGTH_INVALID")
    @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "INVALID_RESOURCE_ID_FORMAT")
    String finalId;

    @Size(min = 2, max = 255, message = "FINAL_NAME_LENGTH_INVALID")
    String finalName;

    String parentSubjectId;

    @Size(max = 2000, message = "REJECTION_REASON_TOO_LONG")
    String rejectionReason;

    @Size(max = 2000, message = "NOTE_TOO_LONG")
    String note;
}
