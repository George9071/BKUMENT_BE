package vn.edu.hcmut.identity.dto.request;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ProfileCreationRequest {
    @NotBlank(message = "INVALID_KEY")
    String accountId;

    @NotBlank(message = "INVALID_KEY")
    String firstName;

    @NotBlank(message = "INVALID_KEY")
    String lastName;

    @Past(message = "INVALID_KEY")
    @JsonFormat(pattern = "yyyy-MM-dd")
    LocalDate dob;

    @NotNull(message = "INVALID_KEY")
    Integer universityId;

    @NotBlank(message = "INVALID_KEY")
    @Email(message = "INVALID_KEY")
    String email;

    @Pattern(regexp = "^\\d{10,11}$", message = "INVALID_KEY")
    String phone;

    String address;

    @Size(max = 500, message = "INVALID_KEY")
    String bio;
}
