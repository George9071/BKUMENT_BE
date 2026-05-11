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
import vn.edu.hcmut.document.event.UserViewedDocumentEvent;

/**
 * Synchronises user interaction events (downloads, views) to the Neo4j knowledge graph and
 * serves collaborative document recommendations via graph traversal.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GraphSyncService {

    private final Neo4jClient neo4jClient;

	/**
	 * Records a document download event in the Neo4j graph asynchronously.
	 */
	@KafkaListener(topics = "document-download-events", groupId = "document-graph-sync-group")
    public void handleDownloadEvent(UserDownloadedDocumentEvent event) {
		String profileId = event.getProfileId();
		String documentId = event.getDocumentId();
		String topicId = event.getTopicId();

		if (profileId == null || documentId == null) {
			log.warn("[GRAPH] Skipping download event with null profileId or documentId");
			return;
		}

		String timeNow = (event.getTimestamp() != null)
				? event.getTimestamp()
				: LocalDateTime.now().toString();

		String baseQuery = """
                MERGE (a:UserProfile {id: $profileId})
                MERGE (d:Document {id: $documentId})
                MERGE (a)-[r:DOWNLOADED]->(d)
                ON CREATE SET r.firstDownloadedAt = datetime($time)
                ON MATCH  SET r.lastDownloadedAt  = datetime($time)
                """;

		String topicQuery = """
                WITH d
                MERGE (t:Topic {id: $topicId})
                MERGE (d)-[:HAS_TOPIC]->(t)
                """;

		String finalQuery = (topicId != null && !topicId.isBlank())
				? baseQuery + topicQuery
				: baseQuery;

		try {
			Map<String, Object> params = new HashMap<>();
			params.put("profileId", profileId);
			params.put("documentId", documentId);
			params.put("time", timeNow);
			if (topicId != null && !topicId.isBlank()) params.put("topicId", topicId);

			neo4jClient.query(finalQuery).bindAll(params).run();
			log.info("[GRAPH] Download synced: user={} -> doc={}", profileId, documentId);
		} catch (Exception e) {
			// TODO: route to Dead Letter Topic for retry.
			log.error("[GRAPH] Download sync failed (user={}, doc={}): {}",
					profileId, documentId, e.getMessage());
		}
    }

	@KafkaListener(topics = "document-view-events", groupId = "document-graph-sync-group")
	public void handleViewEvent(UserViewedDocumentEvent event) {
		String profileId  = event.getProfileId();
		String documentId = event.getDocumentId();

		if (profileId == null || documentId == null) {
			log.warn("[GRAPH] Skipping view event with null profileId or documentId");
			return;
		}

		String timeNow = (event.getTimestamp() != null)
				? event.getTimestamp()
				: LocalDateTime.now().toString();

		String query = """
                MERGE (a:UserProfile {id: $profileId})
                MERGE (d:Document {id: $documentId})
                MERGE (a)-[r:VIEWED]->(d)
                ON CREATE SET r.firstViewedAt = datetime($time), r.viewCount = 1
                ON MATCH  SET r.lastViewedAt  = datetime($time), r.viewCount = r.viewCount + 1
                """;

		try {
			Map<String, Object> params = new HashMap<>();
			params.put("profileId", profileId);
			params.put("documentId", documentId);
			params.put("time", timeNow);

			neo4jClient.query(query).bindAll(params).run();
			log.info("[GRAPH] View synced: user={} -> doc={}", profileId, documentId);
		} catch (Exception e) {
			// TODO: route to Dead Letter Topic for retry.
			log.error("[GRAPH] View sync failed (user={}, doc={}): {}",
					profileId, documentId, e.getMessage());
		}
	}

	/**
	 * Returns up to 50 recommended documents for a user, each enriched with the reason it was recommended
	 *     "id"             	— document ID
	 *     "reasonType"     	— ENROLLED_CLASS | DOWNLOADED
	 *     "reasonTriggerId" 	— ID of the classroom or trigger-document that caused the match
	 * ===========================================================================================
	 * Strategy 1 — Curriculum-based (score = 3, reasonType = ENROLLED_CLASS):
	 *   Documents whose topics overlap with subjects covered by classrooms the user is enrolled in
	 * * * *
	 * Strategy 2 — Social collaborative filtering (score = 7, reasonType = DOWNLOADED):
	 *   Documents downloaded by peers (same university OR mutual-download + social link)
	 * * * *
	 * Score accumulation
	 * If a document appears in both strategies its scores are summed before the final ORDER BY
	 * The reasonType of the HIGHEST-scoring entry for each document is kept
	 * * * *
	 * @param profileId the user to generate recommendations for
	 * @return ranked list of up to 50 result maps; empty list on error or no results
	 */
    public List<Map<String, Object>> getCollaborativeRecommendations(String profileId) {
		String query = """
                CALL {
                    // --- Strategy 1: Curriculum-based ---
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
 
                    // --- Strategy 2A: University peers ---
					WITH $profileId AS pid
					MATCH (me:UserProfile {id: pid})-[:STUDY_AT]->(:University)<-[:STUDY_AT]-(other:UserProfile)
					WHERE other.id <> pid
					WITH DISTINCT me, other
					MATCH (other)-[:DOWNLOADED]->(d:Document)
					WHERE NOT (me)-[:DOWNLOADED]->(d)
					OPTIONAL MATCH (me)-[:DOWNLOADED]->(triggerDoc:Document)<-[:DOWNLOADED]-(other)
					RETURN d, 'DOWNLOADED' AS rType,
						   COALESCE(triggerDoc.id, other.id) AS rTriggerId, 7 AS score

					UNION ALL		
					
					// --- Strategy 2B: Social/mutual peers ---
                    WITH $profileId AS pid
                    MATCH (me:UserProfile {id: pid})
                    MATCH (other:UserProfile)
                    WHERE other.id <> pid
                    AND EXISTS { (me)-[:DOWNLOADED]->(:Document)<-[:DOWNLOADED]-(other) }
                    AND (
                        EXISTS { (me)-[:FOLLOW]->(other) } OR
                        EXISTS { (me)-[:ENROLLED_IN]->(:ClassRoom)<-[:ENROLLED_IN]-(other) }
                    )
                    WITH DISTINCT me, other
                    MATCH (me)-[:DOWNLOADED]->(triggerDoc:Document)<-[:DOWNLOADED]-(other)
                    MATCH (other)-[:DOWNLOADED]->(d:Document)
                    WHERE NOT (me)-[:DOWNLOADED]->(d)
                    RETURN d, 'DOWNLOADED' AS rType, triggerDoc.id AS rTriggerId, 7 AS score	
				}
				
	// For each document, accumulate scores across strategies and keep the reason from the highest-scoring strategy.
				WITH d, rType, rTriggerId, score
				ORDER BY score DESC
				WITH d, sum(score) AS totalScore,
					 head(collect(rType))        AS topReasonType,
					 head(collect(rTriggerId))   AS topReasonTriggerId
				ORDER BY totalScore DESC
				LIMIT 50
				RETURN collect({
					id:              d.id,
					reasonType:      topReasonType,
					reasonTriggerId: topReasonTriggerId
				}) AS results				
                """;

        try {
            Optional<Map<String, Object>> result = neo4jClient
                    .query(query)
                    .bind(profileId)
                    .to("profileId")
                    .fetch()
                    .one();

            if (result.isEmpty()) return List.of();

            @SuppressWarnings("unchecked")
			List<Map<String, Object>> results =
					(List<Map<String, Object>>) result.get().get("results");
			return results != null ? results : List.of();

        } catch (Exception e) {
			log.error("[GRAPH] Recommendations failed for user {}: {}", profileId, e.getMessage());
			return List.of();
        }
    }

	/**
	 * Carries the total document count and the list of recommended IDs
	 * when pagination metadata needs to be returned alongside the IDs.
	 * TODO: Move to a dedicated entity / DTO package rather than keeping it as a nested class here.
	 */
    @Getter
    @AllArgsConstructor
    public static class RecommendationResult {
        private long total;
        private List<String> ids;
    }
}
