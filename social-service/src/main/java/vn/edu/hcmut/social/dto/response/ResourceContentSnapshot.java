package vn.edu.hcmut.social.dto.response;

import java.time.LocalDateTime;

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
public class ResourceContentSnapshot {
    String id;
    String title;
    String content;
    String coverImage;
    String ownerId;
    LocalDateTime createdAt;
    Long views;
}
