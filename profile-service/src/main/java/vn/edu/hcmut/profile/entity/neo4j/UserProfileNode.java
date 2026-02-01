package vn.edu.hcmut.profile.entity.neo4j;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.neo4j.core.schema.*;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Node("UserProfile")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserProfileNode implements Persistable<String> {
    @Id
    String id;

    String fullName;
    String role;

    @Relationship(type = "STUDY_AT", direction = Relationship.Direction.OUTGOING)
    UniversityNode university;

    @Relationship(type = "FOLLOW", direction = Relationship.Direction.OUTGOING)
    @Builder.Default
    Set<UserProfileNode> following = new HashSet<>();

    @Relationship(type = "INTERESTED_IN", direction = Relationship.Direction.OUTGOING)
    List<TopicNode> interestedTopics;

    @Relationship(type = "TEACHES", direction = Relationship.Direction.OUTGOING)
    List<SubjectNode> teachesSubjects;

    @Builder.Default
    List<String> roles = new ArrayList<>();

    @Transient
    boolean isNew = true;

    @Override
    public String getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    public void setNotNew() {
        this.isNew = false;
    }
}
