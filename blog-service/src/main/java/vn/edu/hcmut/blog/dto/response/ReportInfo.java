package vn.edu.hcmut.blog.dto.response;

import java.time.LocalDateTime;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ReportInfo {
    String id;
    String reporterId;
    String status;
    String reason;
    String detail;
    LocalDateTime createdAt;
}
