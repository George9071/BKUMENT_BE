package vn.edu.hcmut.profile.dto.sync;

import lombok.Data;

import java.util.List;

@Data
public class SubTopSyncRequest {
    private List<SubjectSyncRequest> subjects;
    private List<TopicSyncRequest> topics;
}
