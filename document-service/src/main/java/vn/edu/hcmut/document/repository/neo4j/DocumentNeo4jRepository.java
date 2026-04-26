package vn.edu.hcmut.document.repository.neo4j;

import java.util.List;
import java.util.Map;

import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;

import vn.edu.hcmut.document.entity.neo4j.DocumentNode;

public interface DocumentNeo4jRepository extends Neo4jRepository<DocumentNode, String> {

    /**
     * Item-based Collaborative Filtering Query:
     * Tìm những users đã download tài liệu `docId`.
     * Tìm những tài liệu khác mà những users này cũng đã download.
     * Sắp xếp theo số lượng (thế hiện mức độ liên quan).
     */
    @Query(
            """
			MATCH (d:Document {id: $docId})<-[:DOWNLOADED]-(u:UserProfile)-[:DOWNLOADED]->(rec:Document)
			WHERE rec.id <> $docId
			RETURN rec.id AS recommendedDocId, count(u) AS weight
			ORDER BY weight DESC
			LIMIT $limit
			""")
    List<Map<String, Object>> findItemBasedCFRecommendations(@Param("docId") String docId, @Param("limit") int limit);

    /**
     * User-based Collaborative Filtering Query ("For For You" Feed):
     * Tìm những user khác có hành vi download tương tự user hiện tại `$userId`.
     * Xem họ tải tài liệu gì mà `$userId` chưa tải.
     * Trả về danh sách tài liệu gợi ý cá nhân hóa dựa trên cộng đồng.
     */
    @Query(
            """
			MATCH (u:UserProfile {id: $userId})-[:DOWNLOADED]->(doc:Document)<-[:DOWNLOADED]-(similarUser:UserProfile)-[:DOWNLOADED]->(rec:Document)
			WHERE NOT (u)-[:DOWNLOADED]->(rec)
			RETURN rec.id AS recommendedDocId, count(similarUser) AS weight,
				collect(DISTINCT doc.id)[0] AS reasonTriggerId, 'DOWNLOADED' AS reasonType
			ORDER BY weight DESC
			LIMIT $limit
			""")
    List<Map<String, Object>> findUserBasedCFRecommendations(@Param("userId") String userId, @Param("limit") int limit);

    /**
     * Cold-start Layer 2: Gợi ý theo Topic user quan tâm hoặc lớp học đang tham gia.
     */
    @Query(
            """
			MATCH (u:UserProfile {id: $userId})
			OPTIONAL MATCH (u)-[:INTERESTED_IN]->(t1:Topic)<-[:HAS_TOPIC]-(d1:Document)
			OPTIONAL MATCH (u)-[:ENROLLED_IN]->(c:ClassRoom)-[:COVERS]->(t2:Topic)<-[:HAS_TOPIC]-(d2:Document)
			WITH u,
				collect(DISTINCT {id: d1.id, triggerId: t1.id, type: 'INTERESTED_TOPIC'}) +
				collect(DISTINCT {id: d2.id, triggerId: c.id, type: 'ENROLLED_CLASS'}) AS allItems
			UNWIND allItems AS item
			WITH u, item
			WHERE item.id IS NOT NULL AND NOT (u)-[:DOWNLOADED]->(item.id)
			RETURN item.id AS recommendedDocId, item.triggerId AS reasonTriggerId, item.type AS reasonType, count(*) AS weight
			ORDER BY weight DESC
			LIMIT $limit
			""")
    List<Map<String, Object>> findColdStartRecommendationsByTopics(
            @Param("userId") String userId, @Param("limit") int limit);
}
