package vn.edu.hcmut.profile.entity.jpa;

import java.time.LocalDate;

import jakarta.persistence.*;

import lombok.*;
import lombok.experimental.FieldDefaults;
import vn.edu.hcmut.profile.constant.Gender;

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

    @Column(name = "email", unique = true)
    String email;

    @Builder.Default
    @Column(name = "email_verified", nullable = false)
    boolean emailVerified = false;

    LocalDate dob;

    @Column(columnDefinition = "TEXT")
    String bio;

    String avatarUrl;
    String phone;
    String address;

    @Enumerated(EnumType.STRING)
    Gender gender;

    @Builder.Default
    Long points = 0L;

    @Column(name = "university_id")
    Integer universityId;
}
