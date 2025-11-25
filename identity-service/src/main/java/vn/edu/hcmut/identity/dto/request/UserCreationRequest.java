package vn.edu.hcmut.identity.dto.request;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDate;

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
    String university;

}
