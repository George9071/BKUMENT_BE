package vn.edu.hcmut.document.dto.response;

import java.time.ZonedDateTime;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class FileInfoResponse {
    String fileName;
    long size;
    String contentType;
    ZonedDateTime lastModified;
}
