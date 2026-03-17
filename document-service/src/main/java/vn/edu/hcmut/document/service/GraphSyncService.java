package vn.edu.hcmut.document.service;

import java.time.LocalDateTime;
import java.util.*;

import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class GraphSyncService {

    private final Neo4jClient neo4jClient;

    @Async("graphExecutor")
    public void handleDownloadEvent(String profileId, String documentId, String topicId) {
        String timeNow = LocalDateTime.now().toString();

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
            log.error("Lỗi đồng bộ Neo4j: {}", e.getMessage());
        }
    }

    public List<String> getCollaborativeRecommendations(String profileId) {
        String cypherQuery =
                """
			CALL {
				// Gợi ý theo TẤT CẢ Topic của Môn học (thông qua màng lọc bắt cầu)
				WITH $profileId AS pid
				MATCH (me:UserProfile {id: pid})
					-[:ENROLLED_IN]->(:ClassRoom)
					-[:COVERS]->(t_current:Topic)
					-[:BELONGS_TO]->(s:Subject)
					<-[:BELONGS_TO]-(t_all:Topic)
					<-[:HAS_TOPIC]-(d:Document)
				WHERE NOT (me)-[:DOWNLOADED]->(d)
				RETURN d, 3 AS score

				UNION ALL

				// 2. Collaborative Filtering (Điểm: 7)
				WITH $profileId AS pid

				// Tìm những người có chung lịch sử download
				MATCH (me:UserProfile {id: pid})
					-[:DOWNLOADED]->(:Document)
					<-[:DOWNLOADED]-(other:UserProfile)
				WHERE other.id <> pid

				// 1 trong 3 điều kiện
				AND (
					EXISTS { (me)-[:ENROLLED_IN]->(:ClassRoom)<-[:ENROLLED_IN]-(other) } OR
					EXISTS { (me)-[:FOLLOW]->(other) } OR
					EXISTS { (me)-[:STUDY_AT]->(:University)<-[:STUDY_AT]-(other) }
				)

				WITH DISTINCT me, other

				MATCH (other)-[:DOWNLOADED]->(d:Document)
				WHERE NOT (me)-[:DOWNLOADED]->(d)
				RETURN d, 7 AS score
			}

			WITH d, sum(score) AS totalScore
			ORDER BY totalScore DESC
			LIMIT 150
			RETURN collect(d.id) AS allIds
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
            List<String> ids = (List<String>) result.get().get("allIds");
            return ids != null ? ids : List.of();

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
