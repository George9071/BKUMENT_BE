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
public class DocumentMetadataResponse {
    String id;
    String title;
    Author author;
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
    List<String> keywords;
    Long views;
    RecommendationReason recommendationReason;

    String universityId;
    String courseId;
    String topicId;

    vn.edu.hcmut.document.constant.AiAnalyzeStatus deepAiStatus;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class Author {
        String id;
        String name;
        String avatarUrl;
    }
}
