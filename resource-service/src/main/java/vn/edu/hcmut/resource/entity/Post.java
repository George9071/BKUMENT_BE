package vn.edu.hcmut.resource.entity;

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
@Table(name = "post")
public class Post extends Resource {
    @Column(columnDefinition = "TEXT")
    String content;

    @Column(name = "asset_id")
    String assetId;
}
