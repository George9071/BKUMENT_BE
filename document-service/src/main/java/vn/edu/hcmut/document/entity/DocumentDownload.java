package vn.edu.hcmut.document.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;

import org.hibernate.annotations.CreationTimestamp;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Entity
@Table(name = "document_download")
public class DocumentDownload {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    String id;

    @Column(name = "profile_id", columnDefinition = "TEXT")
    String profileId;

    @Column(name = "document_id", columnDefinition = "TEXT")
    String documentId;

    @CreationTimestamp
    @Column(name = "downloaded_at", updatable = false)
    LocalDateTime downloadedAt;
}
