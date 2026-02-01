package vn.edu.hcmut.profile.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import vn.edu.hcmut.profile.dto.sync.SubjectSyncRequest;
import vn.edu.hcmut.profile.dto.sync.TopicSyncRequest;

@Service
@RequiredArgsConstructor
public class SyncService {
    private final Neo4jClient neo4jClient;

    public void syncSubjects(List<SubjectSyncRequest> subjects) {
        String query = """
                UNWIND $data AS row
                MERGE (s:Subject {id: row.id})
                SET s.name = row.name
                """;

        List<Map<String, Object>> parameters = subjects.stream()
                .map(sub -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", sub.getId());
                    map.put("name", sub.getName());
                    return map;
                })
                .toList();

        neo4jClient.query(query)
                .bindAll(Map.of("data", parameters))
                .run();
    }

    public void syncTopics(List<TopicSyncRequest> topics) {
        String query = """
                UNWIND $data AS row
                MERGE (t:Topic {id: row.id})
                SET t.name = row.name
                
                WITH t, row
                MATCH (s:Subject {id: row.subjectId})
                MERGE (t)-[:BELONGS_TO]->(s)
                """;

        List<Map<String, Object>> parameters = topics.stream()
                .map(top -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", top.getId());
                    map.put("name", top.getName());
                    map.put("subjectId", top.getSubjectId());
                    return map;
                })
                .toList();

        neo4jClient.query(query)
                .bindAll(Map.of("data", parameters))
                .run();
    }
}
