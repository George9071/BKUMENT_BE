package vn.edu.hcmut.document.entity;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExtractedItem {
    private String type;
    private String text;
    private ItemMetadata metadata;
}
