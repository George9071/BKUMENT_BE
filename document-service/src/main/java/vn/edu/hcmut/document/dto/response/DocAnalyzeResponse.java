package vn.edu.hcmut.document.dto.response;

import java.util.List;

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
public class DocAnalyzeResponse {
    String docId;

    List<String> keywords;
    String summary;
    vn.edu.hcmut.document.constant.AiAnalyzeStatus deepAiStatus;
}
