package vn.edu.hcmut.profile.entity.neo4j;

import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

import lombok.*;

@Node("Subject")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SubjectNode {
    @Id
    String id;

    String name;
}
