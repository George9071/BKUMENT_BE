package vn.edu.hcmut.identity.dto.request;

import java.time.LocalDate;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ProfileCreationRequest {
    String accountId;
    String firstName;
    String lastName;
    LocalDate dob;
    Integer universityId;
    String email;
    String phone;
    String address;
    String bio;
}
