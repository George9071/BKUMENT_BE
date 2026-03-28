package vn.edu.hcmut.profile.dto.sync;

import java.util.Set;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TutorSubjectSyncRequest {
    String tutorId;
    Set<String> subjectIds;
}
