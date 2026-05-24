package vn.edu.hcmut.lms.service;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import vn.edu.hcmut.lms.dto.sync.ClassRoomSyncRequest;
import vn.edu.hcmut.lms.dto.sync.EnrollmentSyncRequest;
import vn.edu.hcmut.lms.dto.sync.SubjectSyncRequest;
import vn.edu.hcmut.lms.dto.sync.TopicSyncRequest;

@Service
@RequiredArgsConstructor
@Slf4j
public class GraphSyncService {

    private final Neo4jClient neo4jClient;

    /**
     * Creates an ENROLLED_IN relationship between a student and a classroom.
     * Called when a tutor approves an enrollment request.
     * Synchronous — approval requires data consistency.
     */
    public void addEnrollment(String studentId, String classId) {
        String query = """
        MERGE (u:UserProfile {id: $studentId})
        MERGE (c:ClassRoom {id: $classId})
        MERGE (u)-[r:ENROLLED_IN]->(c)
            ON CREATE SET r.enrolledAt = datetime()
        """;

        neo4jClient.query(query)
                .bindAll(Map.of("studentId", studentId, "classId", classId))
                .run();
        log.info("Neo4j: Added ENROLLED_IN for student {} in class {}", studentId, classId);
    }

    /**
     * Removes the ENROLLED_IN relationship between a student and a classroom.
     * Called synchronously — leaveClass / removeStudent requires this to complete.
     */
    public void removeEnrollment(String studentId, String classId) {
        String query = """
            MATCH (u:UserProfile {id: $studentId})-[r:ENROLLED_IN]->(c:ClassRoom {id: $classId})
            DELETE r
            """;
        neo4jClient.query(query)
                .bindAll(Map.of("studentId", studentId, "classId", classId))
                .run();
        log.info("Neo4j: Removed ENROLLED_IN for student {} in class {}", studentId, classId);
    }

    /**
     * Bulk upserts ENROLLED_IN relationships.
     * Used for data migration / reconciliation jobs only.
     */
    public void syncAllEnrollments(List<EnrollmentSyncRequest> requests) {
        if (requests == null || requests.isEmpty()) return;

        neo4jClient.query("MATCH ()-[r:ENROLLED_IN]->() DELETE r").run();

        String query = """
            UNWIND $data AS row
            MERGE (u:UserProfile {id: row.studentId})
            MERGE (c:ClassRoom {id: row.classRoomId})
            MERGE (u)-[:ENROLLED_IN]->(c)
            """;

        List<Map<String, Object>> params = requests.stream()
                .map(r -> Map.<String, Object>of(
                        "studentId",   r.getStudentId(),
                        "classRoomId", r.getClassId()))
                .toList();

        neo4jClient.query(query).bindAll(Map.of("data", params)).run();
        log.info("Neo4j: Bulk synced {} ENROLLED_IN relationships.", requests.size());
    }

    /**
     * Upserts a ClassRoom node and re-links its COVERS → Topic relationship.
     * Called on classroom create and update.
     */
    public void syncClassRoom(ClassRoomSyncRequest request) {
        String query = """
            MERGE (c:ClassRoom {id: $id})
            SET c.name   = $name,
                c.status = $status,
                c.format = $format

            WITH c
            OPTIONAL MATCH (c)-[r:COVERS]->()
            DELETE r

            WITH c
            UNWIND (CASE WHEN $topicId IS NOT NULL THEN [$topicId] ELSE [] END) AS tId
            MERGE (t:Topic {id: tId})
            MERGE (c)-[:COVERS]->(t)
            """;

        Map<String, Object> params = new HashMap<>();
        params.put("id", request.getId());
        params.put("name", request.getName() != null ? request.getName() : "");
        params.put("status", request.getStatus() != null ? request.getStatus() : "");
        params.put("format", request.getFormat() != null ? request.getFormat() : "");
        params.put("topicId", request.getTopicId());

        neo4jClient.query(query)
                .bindAll(params)
                .run();
        log.info("Neo4j: Synced classroom {}.", request.getId());
    }

    /**
     * Detach-deletes a ClassRoom node and all its relationships.
     * Called on classroom hard-delete.
     */
    public void deleteClassRoom(String classId) {
        neo4jClient.query("MATCH (c:ClassRoom {id: $id}) DETACH DELETE c")
                .bindAll(Map.of("id", classId))
                .run();
        log.info("Neo4j: Deleted classroom {} and its relationships.", classId);
    }

    /**
     * Bulk upserts ClassRoom nodes.
     * Used for data migration / reconciliation jobs only.
     */
    public void syncAllClassRooms(List<ClassRoomSyncRequest> requests) {
        if (requests == null || requests.isEmpty()) return;

        String query = """
            UNWIND $data AS row
            MERGE (c:ClassRoom {id: row.id})
            SET c.name   = row.name,
                c.status = row.status,
                c.format = row.format

            WITH c, row
            OPTIONAL MATCH (c)-[r:COVERS]->() DELETE r

            WITH c, row
            UNWIND (CASE WHEN row.topicId IS NOT NULL THEN [row.topicId] ELSE [] END) AS tId
            MERGE (t:Topic {id: tId})
            MERGE (c)-[:COVERS]->(t)
            """;

        List<Map<String, Object>> params = requests.stream()
                .map(r -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id",      r.getId());
                    map.put("name",    r.getName()   != null ? r.getName()   : "");
                    map.put("status",  r.getStatus() != null ? r.getStatus() : "");
                    map.put("format",  r.getFormat() != null ? r.getFormat() : "");
                    map.put("topicId", r.getTopicId()); // nullable
                    return map;
                })
                .toList();

        neo4jClient.query(query).bindAll(Map.of("data", params)).run();
        log.info("Neo4j: Bulk synced {} classrooms.", requests.size());
    }

    /**
     * Replaces all TEACHES relationships for a tutor with the new subject set.
     * Full replace — pass the complete desired subject set each time.
     */
    public void syncTutorSubjects(String tutorId, List<String> subjectIds) {
        String query = """
            MATCH (u:UserProfile {id: $tutorId})
            OPTIONAL MATCH (u)-[r:TEACHES]->() DELETE r

            WITH u
            WHERE $subjectIds IS NOT NULL AND size($subjectIds) > 0
            UNWIND $subjectIds AS subId
            MERGE (s:Subject {id: subId})
            MERGE (u)-[:TEACHES]->(s)
            """;

        neo4jClient.query(query)
                .bindAll(Map.of(
                        "tutorId",    tutorId,
                        "subjectIds", subjectIds != null ? subjectIds : Collections.emptyList()))
                .run();
        log.info("Neo4j: Synced {} subjects for tutor {}.",
                subjectIds != null ? subjectIds.size() : 0, tutorId);
    }

    public void syncSubjects(List<SubjectSyncRequest> subjects) {
        if (subjects == null || subjects.isEmpty()) return;

        String query = """
        UNWIND $data AS row
        MERGE (s:Subject {id: row.id})
        SET s.name = row.name
        """;

        List<Map<String, Object>> params = subjects.stream()
                .map(s -> Map.<String, Object>of(
                        "id",   s.getId(),
                        "name", s.getName()))
                .toList();

        neo4jClient.query(query).bindAll(Map.of("data", params)).run();
        log.info("Neo4j: Synced {} subjects.", subjects.size());
    }

    public void syncTopics(List<TopicSyncRequest> topics) {
        if (topics == null || topics.isEmpty()) return;

        String query = """
        UNWIND $data AS row
        MERGE (t:Topic {id: row.id})
        SET t.name = row.name
        WITH t, row
        MATCH (s:Subject {id: row.subjectId})
        MERGE (t)-[:BELONGS_TO]->(s)
        """;

        List<Map<String, Object>> params = topics.stream()
                .map(t -> Map.<String, Object>of(
                        "id",        t.getId(),
                        "name",      t.getName(),
                        "subjectId", t.getSubjectId()))
                .toList();

        neo4jClient.query(query).bindAll(Map.of("data", params)).run();
        log.info("Neo4j: Synced {} topics with BELONGS_TO relationships.", topics.size());
    }

    public List<String> getRecommendedClassRoomIds(String profileId, int limit) {
        String query = """
            MATCH (u:UserProfile {id: $profileId})-[:DOWNLOADED]->(d:Document)-[:HAS_TOPIC]->(t:Topic)<-[:COVERS]-(c:ClassRoom)
            WHERE NOT (u)-[:ENROLLED_IN]->(c)
              AND (c.status IS NULL OR NOT c.status IN ['COMPLETED', 'CANCELLED'])
            RETURN c.id AS classroomId, count(d) AS weight
            ORDER BY weight DESC
            LIMIT $limit
            """;

        try {
            return neo4jClient.query(query)
                    .bind(profileId).to("profileId")
                    .bind(limit).to("limit")
                    .fetch()
                    .all()
                    .stream()
                    .map(row -> (String) row.get("classroomId"))
                    .toList();
        } catch (Exception e) {
            log.error("Neo4j: Failed to get course recommendations for user {}", profileId, e);
            return List.of();
        }
    }
}
