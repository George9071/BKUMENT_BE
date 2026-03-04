package vn.edu.hcmut.lms.dto.response;

import lombok.*;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TutorSearchResponse {
    TutorResponse tutor;
    List<ClassRoomResponse> matchingClasses;
}
