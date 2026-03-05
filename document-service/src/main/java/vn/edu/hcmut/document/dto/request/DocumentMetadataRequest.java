package vn.edu.hcmut.document.dto.request;

import jakarta.validation.constraints.NotBlank;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DocumentMetadataRequest {
    @NotBlank(message = "Title is required")
    String title;

    @NotBlank(message = "Visibility is required")
    String visibility;

    String description;
    String id;
    String documentType;
    String university;
    String course;
    String summary;
    Boolean downloadable;
    String assetId; // MinIO file ID

    // ID fields for relationships
    String universityId;
    String courseId;
    String topicId;
}
