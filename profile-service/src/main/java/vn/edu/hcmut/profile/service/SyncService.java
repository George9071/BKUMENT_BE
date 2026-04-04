package vn.edu.hcmut.profile.service;

import java.util.List;
import java.util.Map;

import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import vn.edu.hcmut.profile.constant.CypherQueries;
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
     * Bulk upserts Subject nodes into Neo4j.
     * MERGE — idempotent, safe to run multiple times.
     */
    @Transactional
    public void syncSubjects(List<SubjectSyncRequest> subjects) {
        if (subjects == null || subjects.isEmpty()) {
            log.info("No subjects to sync.");
            return;
        }

        neo4jClient
                .query(CypherQueries.SYNC_SUBJECTS)
                .bindAll(Map.of(
                        "data",
                        subjects.stream()
                                .map(s -> Map.<String, Object>of(
                                        "id", s.getId(),
                                        "name", s.getName()))
                                .toList()))
                .run();

        log.info("Synced {} subjects to Neo4j.", subjects.size());
    }

    /**
     * Bulk upserts Topic nodes and BELONGS_TO → Subject relationships.
     * Subjects must exist before calling this — call syncSubjects() first.
     */
    @Transactional
    public void syncTopics(List<TopicSyncRequest> topics) {
        if (topics == null || topics.isEmpty()) {
            log.info("No topics to sync.");
            return;
        }

        neo4jClient
                .query(CypherQueries.SYNC_TOPICS)
                .bindAll(Map.of(
                        "data",
                        topics.stream()
                                .map(t -> Map.<String, Object>of(
                                        "id", t.getId(),
                                        "name", t.getName(),
                                        "subjectId", t.getSubjectId()))
                                .toList()))
                .run();

        log.info("Synced {} topics with BELONGS_TO to Neo4j.", topics.size());
    }

    @Transactional
    public void syncMetadata(List<SubjectSyncRequest> subjects, List<TopicSyncRequest> topics) {
        syncSubjects(subjects);
        syncTopics(topics);
    }
}
