package vn.edu.hcmut.lms.dto.request;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TutorRegistrationRequest {
    String introduction;
    String name;
    String avatar;
    Set<String> subjectIds;
}
