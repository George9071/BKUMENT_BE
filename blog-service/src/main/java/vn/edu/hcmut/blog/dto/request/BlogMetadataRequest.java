package vn.edu.hcmut.blog.dto.request;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

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
    String title;

    @NotBlank(message = "Visibility is required")
    String visibility;

    String content;
    String coverImage;
    String description;

    @NotNull(message = "assetIds must not be null")
    List<String> assetIds;
}
