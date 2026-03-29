package vn.edu.hcmut.communication.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ChatMessageRequest {
    @NotBlank
    String conversationId;

    String message;
    String tempId;

    @NotBlank
    @Pattern(regexp = "^(TEXT|IMAGE|FILE)$", message = "only accepts TEXT, IMAGE, or FILE")
    String type;

    String attachedUrl;
}
