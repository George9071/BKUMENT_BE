package vn.edu.hcmut.social.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import lombok.*;
import lombok.experimental.FieldDefaults;
import vn.edu.hcmut.social.enums.ReportReason;
import vn.edu.hcmut.social.enums.ReportType;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ReportRequest {
    @NotBlank(message = "Target ID cannot be blank")
    String targetId;

    @NotNull(message = "Type cannot be null")
    ReportType type;

    @NotNull(message = "Reason cannot be null")
    ReportReason reason;

    String detail;
}
