package vn.edu.hcmut.profile.entity.jpa;

import java.time.LocalDate;

import jakarta.persistence.*;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Entity
@Table(name = "user_profiles")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserProfile {
    @Id
    String id; // UUID từ Service tạo ra

    @Column(nullable = false, unique = true)
    String accountId;

    String firstName;
    String lastName;
    String email;
    LocalDate dob;

    @Column(columnDefinition = "TEXT")
    String bio;

    String avatarUrl;
    String phone;
    String address;

    @Builder.Default
    Long points = 0L;

    @Column(name = "university_id")
    Integer universityId;
}
