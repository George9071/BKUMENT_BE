package vn.edu.hcmut.document.dto.response;

import java.io.InputStream;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ResourceDownloadResponse {
    String fileName;
    String contentType;
    long fileSize;
    InputStream inputStream;
}
