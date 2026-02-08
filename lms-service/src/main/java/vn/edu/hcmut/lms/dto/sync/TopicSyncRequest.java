package vn.edu.hcmut.lms.dto.sync;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TopicSyncRequest {
    String id;
    String name;
    String subjectId;
}

