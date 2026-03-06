package vn.edu.hcmut.profile.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import vn.edu.hcmut.profile.entity.neo4j.UserProfileNode;

@Repository
public interface UserProfileNodeRepository extends Neo4jRepository<UserProfileNode, String> {
    /**
     * Get followers
     * Cypher direction: (f) -[:FOLLOW]-> (p)
     */
    @Query(
            value = "MATCH (f:UserProfile)-[:FOLLOW]->(p:UserProfile {id: $profileId}) "
                    + "RETURN f SKIP $skip LIMIT $limit",
            countQuery = "MATCH (f:UserProfile)-[:FOLLOW]->(p:UserProfile {id: $profileId}) " + "RETURN count(f)")
    Page<UserProfileNode> findFollowers(@Param("profileId") String profileId, Pageable pageable);

    /**
     * Get followings
     * Cypher direction: (p) -[:FOLLOW]-> (f)
     */
    @Query(
            value = "MATCH (p:UserProfile {id: $profileId})-[:FOLLOW]->(f:UserProfile) "
                    + "RETURN f SKIP $skip LIMIT $limit",
            countQuery = "MATCH (p:UserProfile {id: $profileId})-[:FOLLOW]->(f:UserProfile) " + "RETURN count(f)")
    Page<UserProfileNode> findFollowing(@Param("profileId") String profileId, Pageable pageable);

    @Query("MATCH (f:UserProfile)-[:FOLLOW]->(p:UserProfile {id: $profileId}) RETURN count(f)")
    Integer countFollowers(@Param("profileId") String profileId);

    @Query("MATCH (p:UserProfile {id: $profileId})-[:FOLLOW]->(f:UserProfile) RETURN count(f)")
    Integer countFollowing(@Param("profileId") String profileId);
}
