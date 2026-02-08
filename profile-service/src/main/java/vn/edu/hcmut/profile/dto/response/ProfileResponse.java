package vn.edu.hcmut.profile.dto.response;

import java.time.LocalDate;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ProfileResponse {
    String id;
    String accountId;

    String fullName;
    String firstName;
    String lastName;

    String university;
    Integer universityId;

    LocalDate dob;
    String bio;
    String avatarUrl;
    String email;
    Long points;

    Integer followerCount;
    Integer followingCount;
}
