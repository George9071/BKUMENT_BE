package vn.edu.hcmut.blog.entity;

import jakarta.persistence.*;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Entity
@Table(name = "blog_asset")
public class PostAsset {
    @Id
    @Column
    String id;

    @Column(name = "resource_id", nullable = false)
    String resourceId;
}
