package vn.edu.hcmut.identity.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AccountUpdateRequest {
    @NotBlank(message = "REQUIRED_FIELD")
    @Size(min = 6, max = 20, message = "USERNAME_LENGTH_INVALID")
    String username;

    @NotBlank(message = "REQUIRED_FIELD")
    @Size(min = 8, message = "PASSWORD_LENGTH_INVALID")
    String password;
}
