package vn.edu.hcmut.blog.dto.request;

import java.util.List;

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

    @NotBlank(message = "Visibility is required")
    String visibility;

    @Size(max = 20000, message = "Content must be at most 20000 characters")
    String content;

    String coverImage;

    @Size(max = 1000, message = "Description must be at most 1000 characters")
    String description;

    @NotNull(message = "assetIds must not be null")
    List<String> assetIds;
}
