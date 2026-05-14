package vn.edu.hcmut.document.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;

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
