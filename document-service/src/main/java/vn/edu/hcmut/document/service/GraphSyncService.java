package vn.edu.hcmut.document.service;

import java.time.LocalDateTime;
import java.util.*;

import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import vn.edu.hcmut.document.event.UserDownloadedDocumentEvent;

@Service
@RequiredArgsConstructor
@Slf4j
public class GraphSyncService {

    private final Neo4jClient neo4jClient;

    @KafkaListener(topics = "document-download-events", groupId = "document-graph-sync-group")
    public void handleDownloadEvent(UserDownloadedDocumentEvent event) {
        String profileId = event.getProfileId();
        String documentId = event.getDocumentId();
        String topicId = event.getTopicId();
        String timeNow = event.getTimestamp() != null
                ? event.getTimestamp()
                : LocalDateTime.now().toString();

        String baseQuery =
                """
				MERGE (a:UserProfile {id: $profileId})
				MERGE (d:Document {id: $documentId})
				MERGE (a)-[r:DOWNLOADED]->(d)
				ON CREATE SET r.firstDownloadedAt = datetime($time)
				ON MATCH SET r.lastDownloadedAt = datetime($time)
				""";

        String topicQuery = """
				WITH d
				MERGE (t:Topic {id: $topicId})
				MERGE (d)-[:HAS_TOPIC]->(t)
				""";

        String finalQuery = (topicId != null && !topicId.isBlank()) ? baseQuery + topicQuery : baseQuery;

        try {
            Map<String, Object> params = new HashMap<>();
            params.put("profileId", profileId);
            params.put("documentId", documentId);
            params.put("time", timeNow);

            if (topicId != null && !topicId.isBlank()) {
                params.put("topicId", topicId);
            }

            neo4jClient.query(finalQuery).bindAll(params).run();

            log.info("Neo4j: User {} downloaded Doc {}", profileId, documentId);
        } catch (Exception e) {
            log.error("Lỗi đồng bộ Neo4j (Download): {}", e.getMessage());
        }
    }

    @Async("graphExecutor")
    public void handleViewEvent(String profileId, String documentId) {
        String timeNow = LocalDateTime.now().toString();

        String query =
                """
				MERGE (a:UserProfile {id: $profileId})
				MERGE (d:Document {id: $documentId})
				MERGE (a)-[r:VIEWED]->(d)
				ON CREATE SET r.firstViewedAt = datetime($time), r.viewCount = 1
				ON MATCH SET r.lastViewedAt = datetime($time), r.viewCount = r.viewCount + 1
				""";

        try {
            Map<String, Object> params = new HashMap<>();
            params.put("profileId", profileId);
            params.put("documentId", documentId);
            params.put("time", timeNow);

            neo4jClient.query(query).bindAll(params).run();

            log.info("Neo4j: User {} viewed Doc {}", profileId, documentId);
        } catch (Exception e) {
            log.error("Lỗi đồng bộ Neo4j (View): {}", e.getMessage());
        }
    }

    public List<Map<String, Object>> getCollaborativeRecommendations(String profileId) {
        String cypherQuery =
                """
			CALL {
				WITH $profileId AS pid
				MATCH (me:UserProfile {id: pid})
					-[:ENROLLED_IN]->(c:ClassRoom)
					-[:COVERS]->(t_current:Topic)
					-[:BELONGS_TO]->(s:Subject)
					<-[:BELONGS_TO]-(t_all:Topic)
					<-[:HAS_TOPIC]-(d:Document)
				WHERE NOT (me)-[:DOWNLOADED]->(d)
				RETURN d, 'ENROLLED_CLASS' AS rType, c.id AS rTriggerId, 3 AS score

				UNION ALL

				WITH $profileId AS pid
				MATCH (me:UserProfile {id: pid})
				MATCH (other:UserProfile)
				WHERE other.id <> pid
				AND (
					EXISTS { (me)-[:STUDY_AT]->(:University)<-[:STUDY_AT]-(other) }
					OR
					(
						EXISTS { (me)-[:DOWNLOADED]->(:Document)<-[:DOWNLOADED]-(other) }
						AND (
							EXISTS { (me)-[:FOLLOW]->(other) } OR
							EXISTS { (me)-[:ENROLLED_IN]->(:ClassRoom)<-[:ENROLLED_IN]-(other) }
						)
					)
				)

				WITH DISTINCT me, other
				MATCH (me)-[:DOWNLOADED]->(triggerDoc:Document)<-[:DOWNLOADED]-(other)-[:DOWNLOADED]->(d:Document)
				WHERE NOT (me)-[:DOWNLOADED]->(d)
				RETURN d, 'DOWNLOADED' AS rType, triggerDoc.id AS rTriggerId, 7 AS score
			}

			WITH d, rType, rTriggerId, sum(score) AS totalScore
			ORDER BY totalScore DESC
			LIMIT 150
			RETURN collect({id: d.id, reasonType: rType, reasonTriggerId: rTriggerId}) AS results
			""";

        try {
            Optional<Map<String, Object>> result = neo4jClient
                    .query(cypherQuery)
                    .bind(profileId)
                    .to("profileId")
                    .fetch()
                    .one();

            if (result.isEmpty()) {
                return List.of();
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> results =
                    (List<Map<String, Object>>) result.get().get("results");
            return results != null ? results : List.of();

        } catch (Exception e) {
            log.error("Lỗi lấy gợi ý từ Neo4j: {}", e.getMessage());
            return List.of();
        }
    }

    // TODO: move this to entity
    @Getter
    @AllArgsConstructor
    public static class RecommendationResult {
        private long total;
        private List<String> ids;
    }
}
