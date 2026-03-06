package vn.edu.hcmut.profile.dto.request;

import java.time.LocalDate;

import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.*;
import lombok.experimental.FieldDefaults;
import vn.edu.hcmut.profile.constant.Gender;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ProfileUpdateRequest {
    @Size(min = 1, message = "REQUIRED_FIELD")
    String firstName;

    @Size(min = 1, message = "REQUIRED_FIELD")
    String lastName;

    @Past(message = "INVALID_DOB")
    @JsonFormat(pattern = "yyyy-MM-dd")
    LocalDate dob;

    @Size(max = 500, message = "BIO_LENGTH_INVALID")
    String bio;

    String avatarUrl;

    String address;

    Gender gender;

    @Pattern(regexp = "^\\d{10,11}$", message = "INVALID_PHONE")
    String phone;

    Integer universityId;
}
