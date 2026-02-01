package vn.edu.hcmut.lms.dto.sync;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SubjectSyncRequest {
    String id;
    String name;
}
