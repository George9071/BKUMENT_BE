package vn.edu.hcmut.event;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class EmailSendEvent {
    String recipient;
    String subject;
    String body;
}
