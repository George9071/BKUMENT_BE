package vn.edu.hcmut.document.entity;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ItemMetadata {
    @JsonProperty("page_number")
    private Integer pageNumber;

    private Double confidence;

    @JsonProperty("text_as_html")
    private String textAsHtml;
}
