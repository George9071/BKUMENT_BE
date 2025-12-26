package vn.edu.hcmut.resource.dto.request;

import jakarta.validation.constraints.NotBlank;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ResourceMetadataRequest {
    @NotBlank(message = "Title is required")
    String title;

    @NotBlank(message = "Resource type is required")
    String resourceType;

    @NotBlank(message = "Visibility is required")
    String visibility;

    String content;

    String description;
    String documentType;
    String university;
    String course;
    String summary;
    Boolean downloadable;

    String assetId;
}
