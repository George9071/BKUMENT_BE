package vn.edu.hcmut.identity.dto.request;

import java.time.LocalDate;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserCreationRequest {
    AccountCreationRequest account;
    String firstName;
    String lastName;
    LocalDate dob;
    Integer universityId;
    String email;
    String phone;
    String address;
    String bio;
}
