package vn.edu.hcmut.profile.service;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import vn.edu.hcmut.profile.dto.sync.*;

/**
 * Temporary utility service to synchronize Subject and Topic data
 * from PostgresSQL (or another source) into Neo4j.
 * NOTE: This service is for stabilization purposes and will be removed once data is fully synced.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SyncService {
    private final Neo4jClient neo4jClient;

    /**
     * Synchronizes a batch of Subjects into Neo4j.
     * creates if not exists, updates if exists.
     */
    @Transactional
    public void syncSubjects(List<SubjectSyncRequest> subjects) {
        if (subjects == null || subjects.isEmpty()) {
            log.info("No subjects to sync.");
            return;
        }

        String query = """
				UNWIND $data AS row
				MERGE (s:Subject {id: row.id})
				SET s.name = row.name
				""";

        List<Map<String, Object>> parameters = subjects.stream()
                .map(sub -> Map.<String, Object>of(
                        "id", sub.getId(),
                        "name", sub.getName()))
                .toList();

        neo4jClient.query(query).bindAll(Map.of("data", parameters)).run();
        log.info("Successfully synced {} subjects to Neo4j.", subjects.size());
    }

    /**
     * Synchronizes a batch of topics and links them to their corresponding subjects.
     */
    @Transactional
    public void syncTopics(List<TopicSyncRequest> topics) {
        if (topics == null || topics.isEmpty()) {
            log.info("No topics to sync.");
            return;
        }

        String query =
                """
				UNWIND $data AS row

				// upsert the TOPIC node
				MERGE (t:Topic {id: row.id})
				SET t.name = row.name

				WITH t, row

				// Find the parent SUBJECT node
				MATCH (s:Subject {id: row.subjectId})

				// Create the relationship
				MERGE (t)-[:BELONGS_TO]->(s)
				""";

        List<Map<String, Object>> parameters = topics.stream()
                .map(top -> Map.<String, Object>of(
                        "id", top.getId(),
                        "name", top.getName(),
                        "subjectId", top.getSubjectId()))
                .toList();

        neo4jClient.query(query).bindAll(Map.of("data", parameters)).run();
        log.info("Successfully synced {} topics and their relationships to Neo4j.", topics.size());
    }

    @Transactional
    public void syncTutorSubjects(List<TutorSubjectSyncRequest> requests) {
        if (requests == null || requests.isEmpty()) return;

        String query = """
                UNWIND $data AS row
                MATCH (u:UserProfile {id: row.tutorId})
                
                OPTIONAL MATCH (u)-[r:TEACHES]->()
                DELETE r
                
                WITH u, row
                WHERE row.subjectIds IS NOT NULL AND size(row.subjectIds) > 0
                
                UNWIND row.subjectIds AS subId
                
                MERGE (s:Subject {id: subId})
                MERGE (u)-[:TEACHES]->(s)
                """;

        List<Map<String, Object>> parameters = requests.stream()
                .map(req -> Map.of(
                        "tutorId", req.getTutorId(),
                        "subjectIds", req.getSubjectIds() != null ? req.getSubjectIds() : Collections.emptySet()
                ))
                .toList();

        neo4jClient.query(query).bindAll(Map.of("data", parameters)).run();
        log.info("Successfully synced {} tutor-subject relationships to Neo4j.", requests.size());
    }

    @Transactional
    public void syncClass(ClassRoomSyncRequest request) {
        String query = """
                MERGE (c:ClassRoom {id: $id})
                SET c.name = $name,
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

        neo4jClient.query(query).bindAll(Map.of(
                "id", request.getId(),
                "name", request.getName(),
                "status", request.getStatus() != null ? request.getStatus() : "",
                "format", request.getFormat() != null ? request.getFormat() : "",
                "topicId", request.getTopicId()
        )).run();

        log.info("Successfully synced classroom {} to Neo4j.", request.getId());
    }

    @Transactional
    public void syncClasses(List<ClassRoomSyncRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            log.info("No classrooms to sync.");
            return;
        }

        String query = """
                UNWIND $data AS row
               \s
                MERGE (c:ClassRoom {id: row.id})
                SET c.name = row.name,
                    c.status = row.status,
                    c.format = row.format
                   \s
                WITH c, row
               \s
                OPTIONAL MATCH (c)-[r:COVERS]->()
                DELETE r
               \s
                WITH c, row
               \s
                UNWIND (CASE WHEN row.topicId IS NOT NULL THEN [row.topicId] ELSE [] END) AS tId
               \s
                MERGE (t:Topic {id: tId})
                MERGE (c)-[:COVERS]->(t)
               \s""";

        List<Map<String, Object>> parameters = requests.stream()
                .map(req -> {
                    Map<String, Object> map = new java.util.HashMap<>();
                    map.put("id", req.getId());
                    map.put("name", req.getName() != null ? req.getName() : "");
                    map.put("status", req.getStatus() != null ? req.getStatus() : "");
                    map.put("format", req.getFormat() != null ? req.getFormat() : "");
                    map.put("topicId", req.getTopicId()); // Có thể null
                    return map;
                })
                .toList();

        neo4jClient.query(query).bindAll(Map.of("data", parameters)).run();
        log.info("Successfully bulk synced {} classrooms to Neo4j.", requests.size());
    }

    @Transactional
    public void syncAllEnrollments(List<EnrollmentSyncRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            log.info("No enrollments to sync.");
            return;
        }

        neo4jClient.query("MATCH ()-[r:ENROLLED_IN]->() DELETE r").run();

        String query = """
                UNWIND $data AS row

                MERGE (u:UserProfile {id: row.studentId})
                MERGE (c:ClassRoom {id: row.classRoomId})
                MERGE (u)-[:ENROLLED_IN]->(c)
                """;

        List<Map<String, Object>> parameters = requests.stream()
                .map(req -> Map.<String, Object>of(
                        "studentId", req.getStudentId(),
                        "classRoomId", req.getClassId()
                ))
                .toList();

        neo4jClient.query(query).bindAll(Map.of("data", parameters)).run();
        log.info("Successfully bulk synced {} ENROLLED_IN relationships to Neo4j.", requests.size());
    }

    @Transactional
    public void addEnrollment(EnrollmentSyncRequest request) {
        String query = """
                MERGE (u:UserProfile {id: $studentId})
                MERGE (c:ClassRoom {id: $classId})
                MERGE (u)-[:ENROLLED_IN]->(c)
                """;
        neo4jClient.query(query).bindAll(Map.of(
                "studentId", request.getStudentId(),
                "classId", request.getClassId()
        )).run();
        log.info("Created ENROLLED_IN relation for student {} and class {}",
                request.getStudentId(), request.getClassId());
    }

    @Transactional
    public void removeEnrollment(String studentId, String classId) {
        String query = """
                MATCH (u:UserProfile {id: $studentId})-[r:ENROLLED_IN]->(c:ClassRoom {id: $classId})
                DELETE r
                """;
        neo4jClient.query(query).bindAll(Map.of(
                "studentId", studentId,
                "classId", classId
        )).run();
        log.info("Removed ENROLLED_IN relation for student {} and class {}", studentId, classId);
    }

    @Transactional
    public void deleteClassRoom(String classId) {
        String query = """
                MATCH (c:ClassRoom {id: $id})
                DETACH DELETE c
                """;

        neo4jClient.query(query).bindAll(Map.of("id", classId)).run();
        log.info("Successfully deleted classroom {} and its relationships from Neo4j.", classId);
    }
}
