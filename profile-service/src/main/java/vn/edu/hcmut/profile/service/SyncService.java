package vn.edu.hcmut.profile.service;

import java.util.List;
import java.util.Map;

import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import vn.edu.hcmut.profile.dto.sync.SubjectSyncRequest;
import vn.edu.hcmut.profile.dto.sync.TopicSyncRequest;

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
}
