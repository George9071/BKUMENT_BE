package vn.edu.hcmut.social.dto.request;

import jakarta.validation.constraints.NotNull;

import lombok.*;
import lombok.experimental.FieldDefaults;
import vn.edu.hcmut.social.enums.ReportStatus;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ReportStatusUpdateRequest {
    @NotNull(message = "Status cannot be null")
    ReportStatus status;
}
