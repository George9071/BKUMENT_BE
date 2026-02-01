package vn.edu.hcmut.lms.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import java.util.Set;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Subject {
    @Id
    @Column(length = 50)
    String id;

    String name;

    @OneToMany(mappedBy = "subject", cascade = CascadeType.ALL)
    Set<Topic> topics;
}
