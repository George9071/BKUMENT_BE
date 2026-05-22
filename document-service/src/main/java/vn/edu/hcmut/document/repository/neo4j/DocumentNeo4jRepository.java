package vn.edu.hcmut.document.repository.neo4j;

import java.util.List;
import java.util.Map;

import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;

import vn.edu.hcmut.document.entity.neo4j.DocumentNode;

/**
 * Index recommendations (run in Neo4j browser or migration script):
 *   CREATE INDEX idx_user_profile_id IF NOT EXISTS FOR (u:UserProfile) ON (u.id);
 *   CREATE INDEX idx_document_id     IF NOT EXISTS FOR (d:Document)     ON (d.id);
 *   CREATE INDEX idx_topic_id        IF NOT EXISTS FOR (t:Topic)        ON (t.id);
 *   CREATE INDEX idx_classroom_id    IF NOT EXISTS FOR (c:ClassRoom)    ON (c.id);
 */
public interface DocumentNeo4jRepository extends Neo4jRepository<DocumentNode, String> {

    /**
     * Item-based Collaborative Filtering — "Users who downloaded this also downloaded…"
     * * * *
     * Pattern:
     *   (target document) ←[:DOWNLOADED]— (user) —[:DOWNLOADED]→ (recommended document)
     * * * *
     * Performance note:
     *   The two-hop traversal (doc → user → rec) scales with the number of users who
     *   downloaded $docId. For very popular documents this can be thousands of users,
     *   each potentially having hundreds of downloads. If query time becomes an issue,
     *   add a relationship property filter (e.g. only users who downloaded within 30 days)
     *   to reduce the traversal fan-out.
     * * * *
     * @param docId  the source document whose co-downloaders to traverse
     * @param limit  maximum number of recommendations to return
     * @return list of maps with keys: recommendedDocId (String), weight (Long)
     */
    @Query(
            """
   MATCH (d:Document {id: $docId})<-[:DOWNLOADED]-(u:UserProfile)-[:DOWNLOADED]->(rec:Document)
            WHERE rec.id <> $docId
             AND datetime(r.lastDownloadedAt) >= datetime() - duration({days: 30})
            RETURN rec.id AS recommendedDocId, count(DISTINCT u) AS weight
            ORDER BY weight DESC
            LIMIT $limit
   """)
    List<Map<String, Object>> findItemBasedCFRecommendations(
            @Param("docId") String docId, @Param("limit") int limit);

    /**
     * User-based Collaborative Filtering — "For You" feed
	 * * * *
	 * Pattern:
	 *   (user) —[:DOWNLOADED]→ (bridge doc) ←[:DOWNLOADED]— (similar user) —[:DOWNLOADED]→ (recommended doc)
	 * * * *
	 * The "bridge document" is the common download that connects the target user to a similar user.
	 * Logic verification:
	 *   - Only recommends documents the target user has NOT yet downloaded.
	 *   - Returns reasonTriggerId (bridge doc ID) for the "why" explanation in the UI.
	 *   - Returns reasonType = 'DOWNLOADED' consistently.
	 *   - Ranks by number of distinct similar users who downloaded the recommended doc.
	 * * * *
	 * @param userId the target user's profile ID
	 * @param limit  maximum number of recommendations to return
	 * @return list of maps with keys: recommendedDocId (String), weight (Long),
	 *         reasonTriggerId (String), reasonType (String = "DOWNLOADED"
     */
    @Query(
            """
      MATCH (u:UserProfile {id: $userId})-[:DOWNLOADED]->(doc:Document)<-[:DOWNLOADED]-(similarUser:UserProfile)
                  -[:DOWNLOADED]->(rec:Document)
            WHERE NOT (u)-[:DOWNLOADED]->(rec)
            RETURN rec.id             AS recommendedDocId,
                   count(DISTINCT similarUser) AS weight,
                   min(doc.id)        AS reasonTriggerId,
                   'DOWNLOADED'       AS reasonType
            ORDER BY weight DESC
            LIMIT $limit
   """)
	List<Map<String, Object>> findUserBasedCFRecommendations(
			@Param("userId") String userId, @Param("limit") int limit);

	/**
	 * Cold-start layer 2 — Topic interest and classroom enrollment recommendations.
	 * * * *
	 * Recommends documents that match topics the user has explicitly marked as interests or
	 * Topics covered by classrooms the user is enrolled in.
	 * * * *
	 * @param userId the target user's profile ID
	 * @param limit  maximum number of recommendations to return
	 * @return list of maps with keys: recommendedDocId (String), reasonTriggerId (String),
	 *         reasonType (String = "INTERESTED_TOPIC" | "ENROLLED_CLASS"), weight (Long)
	 */
	@Query(
			"""
            MATCH (u:UserProfile {id: $userId})
            OPTIONAL MATCH (u)-[:INTERESTED_IN]->(t1:Topic)<-[:HAS_TOPIC]-(d1:Document)
            OPTIONAL MATCH (u)-[:ENROLLED_IN]->(c:ClassRoom)-[:COVERS]->(t2:Topic)<-[:HAS_TOPIC]-(d2:Document)
            WHERE c.status IS NULL OR NOT c.status IN ['COMPLETED', 'CANCELLED']
            WITH u,
                 collect(DISTINCT {id: d1.id, triggerId: t1.id,  type: 'INTERESTED_TOPIC'}) +
                 collect(DISTINCT {id: d2.id, triggerId: c.id,   type: 'ENROLLED_CLASS'  }) AS allItems
            UNWIND allItems AS item
            WITH u, item
            WHERE item.id IS NOT NULL
            MATCH (recDoc:Document {id: item.id})
            WHERE NOT (u)-[:DOWNLOADED]->(recDoc)
            RETURN item.id        AS recommendedDocId,
                   item.triggerId AS reasonTriggerId,
                   item.type      AS reasonType,
                   count(*)       AS weight
            ORDER BY weight DESC
            LIMIT $limit
            """)
	List<Map<String, Object>> findColdStartRecommendationsByTopics(
			@Param("userId") String userId, @Param("limit") int limit);
}
