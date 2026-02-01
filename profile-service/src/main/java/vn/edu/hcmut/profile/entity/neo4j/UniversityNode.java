package vn.edu.hcmut.profile.entity.neo4j;

import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

import lombok.*;

@Node("University")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UniversityNode {
    @Id
    Integer id;

    String name;
    String abbreviation;
}
