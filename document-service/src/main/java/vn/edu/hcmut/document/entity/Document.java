package vn.edu.hcmut.document.entity;

import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.ColumnTransformer;

import lombok.*;
import lombok.experimental.FieldDefaults;
import vn.edu.hcmut.document.converter.StringListConverter;
import vn.edu.hcmut.document.converter.VectorConverter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Entity
@SuperBuilder
@Table(name = "document")
public class Document extends Resource {
    @Column(columnDefinition = "TEXT")
    String content;

    @Column(name = "embedding", columnDefinition = "vector(768)")
    @Convert(converter = VectorConverter.class)
    @ColumnTransformer(write = "?::vector")
    private float[] embedding;

    @Column(name = "keywords", columnDefinition = "TEXT")
    @Convert(converter = StringListConverter.class)
    private List<String> keywords;

    @Column(name = "description", columnDefinition = "TEXT")
    String description;

    @Column(name = "preview_image_url")
    String previewImageUrl;

    @Column(name = "download_count")
    Integer downloadCount = 0;

    @Column(name = "is_downloadable")
    Boolean downloadable = true;

    @Column(name = "asset_id")
    String assetId;

    @Column(columnDefinition = "TEXT")
    String summary;

    @Column(columnDefinition = "TEXT")
    String university;

    @Column(columnDefinition = "TEXT")
    String course;

    @Column(name = "document_type")
    String documentType;
}
