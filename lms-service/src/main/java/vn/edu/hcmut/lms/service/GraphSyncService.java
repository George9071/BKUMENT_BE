package vn.edu.hcmut.lms.service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class GraphSyncService {

    private final Neo4jClient neo4jClient;

    @Async("graphExecutor")
    public void handleEnrollmentEvent(String profileId, String classroomId, String topicId) {
        String query = """
            MERGE (u:UserProfile {id: $profileId})
            MERGE (c:ClassRoom {id: $classroomId})
            MERGE (t:Topic {id: $topicId})
            
            MERGE (u)-[r:ENROLLED_IN]->(c)
            ON CREATE SET r.enrolledAt = datetime()
            
            MERGE (c)-[:COVERS]->(t)
        """;

        try {
            Map<String, Object> params = new HashMap<>();
            params.put("profileId", profileId);
            params.put("classroomId", classroomId);
            params.put("topicId", topicId);
            params.put("time", LocalDateTime.now().toString());

            neo4jClient.query(query).bindAll(params).run();
            log.info("Neo4j: User {} enrolled in Class {} with TopenId {}", profileId, classroomId, topicId);
        } catch (Exception e) {
            log.error("Lỗi đồng bộ Enrollment Neo4j: {}", e.getMessage());
        }
    }
}
