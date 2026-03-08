package vn.edu.hcmut.lms.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationRequest {
    @NotBlank(message = "REQUIRED_FIELD")
    String title;

    @NotBlank(message = "REQUIRED_FIELD")
    String message;
}
