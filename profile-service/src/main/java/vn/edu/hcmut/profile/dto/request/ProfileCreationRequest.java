package vn.edu.hcmut.profile.dto.request;

import java.time.LocalDate;

import jakarta.validation.constraints.*;

import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.*;
import lombok.experimental.FieldDefaults;
import vn.edu.hcmut.profile.constant.Gender;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ProfileCreationRequest {

    @NotBlank(message = "REQUIRED_FIELD")
    String accountId;

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

    @Pattern(regexp = "^\\d{10,11}$", message = "INVALID_PHONE")
    String phone;

    String address;

    String avatar;

    Gender gender;

    @Size(max = 500, message = "BIO_LENGTH_INVALID")
    String bio;
}
