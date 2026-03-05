package vn.edu.hcmut.identity.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import lombok.*;
import lombok.experimental.FieldDefaults;
import vn.edu.hcmut.identity.constant.UserRole;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AccountCreationRequest {
    @NotBlank(message = "REQUIRED_FIELD")
    @Size(min = 6, max = 20, message = "USERNAME_LENGTH_INVALID")
    String username;

    @NotBlank(message = "REQUIRED_FIELD")
    @Size(min = 8, message = "PASSWORD_LENGTH_INVALID")
    String password;

    @NotNull(message = "INVALID_ROLE")
    UserRole role;
}
