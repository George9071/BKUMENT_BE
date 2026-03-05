package vn.edu.hcmut.identity.dto.request;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserCreationRequest {
    @Valid
    @NotNull(message = "INVALID_KEY")
    AccountCreationRequest account;

    @NotBlank(message = "REQUIRED_FIELD")
    String firstName;

    @NotBlank(message = "REQUIRED_FIELD")
    String lastName;

    @Past(message = "INVALID_DOB")
    @JsonFormat(pattern = "yyyy-MM-dd")
    LocalDate dob;

    @NotNull(message = "REQUIRED_FIELD")
    Integer universityId;

    @NotBlank(message = "REQUIRED_FIELD")
    @Email(message = "INVALID_EMAIL")
    String email;

    @Pattern(regexp = "^\\d{10,11}$", message = "INVALID_KEY")
    String phone;

    String address;

    @Size(max = 500, message = "BIO_LENGTH_INVALID")
    String bio;
}
