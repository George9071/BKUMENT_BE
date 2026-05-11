package vn.edu.hcmut.blog.dto.request;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class BlogMetadataRequest {

    @NotBlank(message = "Title is required")
    @Size(max = 255, message = "Title must be at most 255 characters")
    String title;

    /**
     * Allowed values: PUBLIC, PRIVATE, INTERNAL.
     * INTERNAL requires courseId to be present; all other values require courseId to be null.
     */
    @NotBlank(message = "Visibility is required")
    String visibility;

    @Size(max = 20000, message = "Content must be at most 20000 characters")
    String content;

    String coverImage;

    @Size(max = 1000, message = "Description must be at most 1000 characters")
    String description;

    @NotNull(message = "assetIds must not be null")
    List<String> assetIds;

    @NotBlank(message = "Topic is required")
    String topicId;

    String universityId;

    String courseId;

    /**
     * Valid combinations:
     *   visibility = INTERNAL  --> courseId IS NOT NULL
     *   visibility = otherwise --> courseId IS NULL
     * * * *
     * If validation fails, Spring returns 400 before the service ever sees the request
     */
    @JsonIgnore
    @AssertTrue(message =
            "courseId must be present when visibility is INTERNAL, "
                    + "and absent for any other visibility value")
    public boolean isVisibilityConsistentWithCourse() {
        // Skip if visibility is missing — @NotBlank on visibility already reports that.
        if (visibility == null) return true;

        boolean isInternal  = "INTERNAL".equalsIgnoreCase(visibility);
        boolean hasCourseId = (courseId != null && !courseId.isBlank());
        return isInternal == hasCourseId;
    }
}
