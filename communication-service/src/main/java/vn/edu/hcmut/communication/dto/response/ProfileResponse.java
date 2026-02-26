package vn.edu.hcmut.communication.dto.response;

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
    String firstName;
    String lastName;
    String avatarUrl;
    String email;
}
