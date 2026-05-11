package vn.edu.hcmut.resource.dto.response;

import java.util.Map;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PresignResponse {
    String assetId;
    String url;
    Map<String, String> formData;
}
