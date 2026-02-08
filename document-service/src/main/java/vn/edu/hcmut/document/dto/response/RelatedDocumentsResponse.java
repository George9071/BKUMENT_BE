package vn.edu.hcmut.document.dto.response;

import java.time.LocalDateTime;
import java.util.List;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RelatedDocumentsResponse {
    String id;
    String title;
    String authorId;
    String documentType;
    String university;
    String course;
    String previewImageUrl;
    Integer downloadCount;
    String downloadUrl;
    LocalDateTime createdAt;
    String description;
    String summary;
    boolean downloadable;
    List<String> keywords;
}
