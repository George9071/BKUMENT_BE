package vn.edu.hcmut.identity.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;
import vn.edu.hcmut.identity.constant.UserRole;

import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AccountResponse {
    String id;
    String username;
    Set<UserRole> roles;
}
