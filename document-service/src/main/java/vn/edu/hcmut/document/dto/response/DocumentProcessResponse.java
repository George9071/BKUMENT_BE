package vn.edu.hcmut.document.dto.response;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentProcessResponse {
    private String filename;
    private List<String> keywords;
    private String summary;
    private String content;
    private List<Double> vector;
}
