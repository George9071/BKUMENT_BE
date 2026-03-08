package vn.edu.hcmut.profile.dto.sync;

import lombok.*;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TutorSubjectSyncRequest {
    String tutorId;
    Set<String> subjectIds;
}
