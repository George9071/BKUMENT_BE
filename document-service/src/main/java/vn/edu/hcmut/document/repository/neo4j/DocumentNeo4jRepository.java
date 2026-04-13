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
			MATCH (d:DocumentNode {id: $docId})<-[:DOWNLOADED]-(u:UserNode)-[:DOWNLOADED]->(rec:DocumentNode)
			WHERE rec.id <> $docId
			RETURN rec.id AS recommendedDocId, count(u) AS weight
			ORDER BY weight DESC
			LIMIT $limit
			""")
    List<Map<String, Object>> findItemBasedCFRecommendations(@Param("docId") String docId, @Param("limit") int limit);

    /**
     * User-based Collaborative Filtering Query ("For You" Feed):
     * Tìm những user khác có hành vi download tương tự user hiện tại `$userId`.
     * Xem họ tải tài liệu gì mà `$userId` chưa tải.
     * Trả về danh sách tài liệu gợi ý cá nhân hóa dựa trên cộng đồng.
     */
    @Query(
            """
			MATCH (u:UserNode {id: $userId})-[:DOWNLOADED]->(doc:DocumentNode)<-[:DOWNLOADED]-(similarUser:UserNode)-[:DOWNLOADED]->(rec:DocumentNode)
			WHERE NOT (u)-[:DOWNLOADED]->(rec)
			RETURN rec.id AS recommendedDocId, count(similarUser) AS weight
			ORDER BY weight DESC
			LIMIT $limit
			""")
    List<Map<String, Object>> findUserBasedCFRecommendations(@Param("userId") String userId, @Param("limit") int limit);
}
