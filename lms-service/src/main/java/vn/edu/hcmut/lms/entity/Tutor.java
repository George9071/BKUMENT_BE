package vn.edu.hcmut.lms.entity;

import lombok.*;
import lombok.experimental.FieldDefaults;
import jakarta.persistence.*;
import org.springframework.data.domain.Persistable;

import java.util.Set;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Tutor implements Persistable<String> {
    @Id
    String id; // profile_id

    String introduction;
    String experience;
    String cvUrl;
    String name;
    String avatar;

    @Builder.Default
    String status = "ACTIVE";

    @Builder.Default
    Double averageRating = 0.0;

    @Builder.Default
    Integer ratingCount = 0;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "tutor_subjects", joinColumns = @JoinColumn(name = "tutor_id"))
    @Column(name = "subject_id")
    Set<String> subjectIds;

    @Transient
    @Builder.Default
    boolean isNew = true;

    @Override
    public String getId() { return id; }

    @Override
    public boolean isNew() { return isNew; }

    @PostLoad
    @PostPersist
    void markNotNew() {this.isNew = false;}

}
