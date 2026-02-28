package vn.edu.hcmut.document.dto.response;

import java.time.LocalDateTime;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DocumentMetadataResponse {
    String id;
    String title;
    String authorId;
    String documentType;
    String university;
    String course;
    Integer downloadCount;
    String downloadUrl;
    String viewUrl;
    String previewImageUrl;
    LocalDateTime createdAt;
    String description;
    String summary;
    boolean downloadable;

    // ID fields for relationships
    String universityId;
    String courseId;
    String topicId;
}
