package vn.edu.hcmut.social.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ResourceCleanupResponse {
    String resourceId;
    int deletedRatings;
    int deletedComments;
    int deletedReports;
}
