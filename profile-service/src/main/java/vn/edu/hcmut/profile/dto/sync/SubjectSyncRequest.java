package vn.edu.hcmut.profile.dto.sync;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SubjectSyncRequest {
    String id;
    String name;
}
