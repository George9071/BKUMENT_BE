package vn.edu.hcmut.resource.dto.response;

import java.time.LocalDateTime;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DocumentResponse {
    String title;
    String description;
    String documentType;
    String university;
    String course;
    int downloadCount;
    boolean downloadable;
    String downloadUrl;
    LocalDateTime createdAt;
}
