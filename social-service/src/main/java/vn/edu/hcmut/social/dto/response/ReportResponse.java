package vn.edu.hcmut.social.dto.response;

import java.time.LocalDateTime;

import lombok.*;
import lombok.experimental.FieldDefaults;
import vn.edu.hcmut.social.enums.ReportReason;
import vn.edu.hcmut.social.enums.ReportStatus;
import vn.edu.hcmut.social.enums.ReportType;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ReportResponse {
    String id;
    String resolverId;
    String reporterId;
    String targetId;
    ReportStatus status;
    ReportType type;
    ReportReason reason;
    String detail;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}
