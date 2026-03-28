package vn.edu.hcmut.communication.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ConversationRequest {
    @Size(max = 100, message = "group name must not exceed 100 characters")
    String name;

    String avatar;

    @NotBlank
    @Pattern(regexp = "^(DIRECT|GROUP)$", message = "only accepts DIRECT or GROUP")
    String type;

    @Size(min = 1)
    @NotNull
    List<String> participantIds;
}
