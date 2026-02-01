package vn.edu.hcmut.profile.entity.neo4j;

import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;

import lombok.*;

@Node("Topic")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TopicNode {
    @Id
    String id;

    String name;

    @Relationship(type = "BELONGS_TO", direction = Relationship.Direction.OUTGOING)
    SubjectNode subject;
}
