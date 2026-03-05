package vn.edu.hcmut.document.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UniversityResponse {
    Integer id;
    String name;
    String abbreviation;
    String logoUrl;
}
