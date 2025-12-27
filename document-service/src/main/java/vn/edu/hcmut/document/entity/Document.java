package vn.edu.hcmut.document.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Entity
@Table(name = "document")
public class Document extends Resource {
    @Column(name = "description", columnDefinition = "TEXT")
    String description;

    @Column(name = "download_count")
    Integer downloadCount = 0;

    @Column(name = "is_downloadable")
    boolean downloadable;

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
