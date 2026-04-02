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
public class ProfileUpdatedEvent {
    String profileId;
    String firstName;
    String lastName;
    String avatar;
    LocalDate dob;
    String bio;
    String address;
    Gender gender;
    String phone;
}
