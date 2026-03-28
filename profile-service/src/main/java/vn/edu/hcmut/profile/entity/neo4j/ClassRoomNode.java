package vn.edu.hcmut.profile.entity.neo4j;

import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Node("ClassRoom")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ClassRoomNode {
    @Id
    String id;

    String name;

    String status;

    String format;

    @Relationship(type = "COVERS", direction = Relationship.Direction.OUTGOING)
    TopicNode topic;
}
