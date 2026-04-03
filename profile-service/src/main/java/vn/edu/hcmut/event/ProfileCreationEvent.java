package vn.edu.hcmut.event;

import java.time.LocalDate;

import lombok.*;
import lombok.experimental.FieldDefaults;
import vn.edu.hcmut.profile.constant.Gender;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ProfileCreationEvent {
    String accountId;
    String firstName;
    String lastName;
    String email;
    LocalDate dob;
    String bio;
    String avatarUrl;
    String phone;
    String address;
    Gender gender;
    Integer universityId;
}
