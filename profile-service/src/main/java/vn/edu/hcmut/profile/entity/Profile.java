// package vn.edu.hcmut.profile.entity;
//
// import java.time.LocalDate;
// import java.util.HashSet;
// import java.util.Set;
//
// import jakarta.persistence.EnumType;
// import jakarta.persistence.Enumerated;
// import org.springframework.data.neo4j.core.schema.*;
// import org.springframework.data.neo4j.core.support.UUIDStringGenerator;
//
// import lombok.*;
// import lombok.experimental.FieldDefaults;
// import vn.edu.hcmut.profile.constant.Gender;
// import vn.edu.hcmut.profile.entity.neo4j.UserProfileNode;
//
// @Getter
// @Setter
// @Builder
// @NoArgsConstructor
// @AllArgsConstructor
// @FieldDefaults(level = AccessLevel.PRIVATE)
// @Node("user_profile")
// public class Profile {
//    @Id
//    @GeneratedValue(generatorClass = UUIDStringGenerator.class)
//    String id;
//
//    @Property("accountId")
//    String accountId;
//
//    String firstName;
//    String lastName;
//    LocalDate dob;
//
//    String bio;
//    String avatarUrl;
//    String phone;
//    String address;
//    String email;
//    String university;
//
//    @Enumerated(EnumType.STRING)
//    Gender gender;
//
//    @Builder.Default
//    Long points = 0L;
//
//    @Relationship(type = "FOLLOW", direction = Relationship.Direction.OUTGOING)
//    @Builder.Default
//    Set<UserProfileNode> following = new HashSet<>();
// }
