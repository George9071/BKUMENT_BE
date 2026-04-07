package vn.edu.hcmut.document.entity.neo4j;

import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Node("Document")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DocumentNode {
    @Id
    String id;

    String title;
    String documentType;
}
