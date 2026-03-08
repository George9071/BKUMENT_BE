package vn.edu.hcmut.social.dto.request;

import jakarta.validation.constraints.NotBlank;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CommentRequest {
    String replyId;

    @NotBlank(message = "Content cannot be blank")
    String content;

    @NotBlank(message = "Resource ID cannot be blank")
    String resourceId;
}
