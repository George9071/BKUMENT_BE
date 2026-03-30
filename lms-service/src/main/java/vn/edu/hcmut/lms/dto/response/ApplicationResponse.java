package vn.edu.hcmut.lms.dto.response;

import lombok.*;
import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationResponse {
    private String id;
    private String profileId;
    private String introduction;
    private String experience;
    private String cvUrl;
    private List<String> subjectIds;
    private String name;
    private String avatar;
    private String status;
    private String rejectionReason;
    private Instant createdAt;
}
