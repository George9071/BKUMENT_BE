package vn.edu.hcmut.profile.constant;

/**
 * Centralized repository for all Neo4j Cypher queries used in Profile Service.
 * Organization:
 *   - Grouped by domain (UserProfile, Follow, Recommendation, Sync)
 *   - Each query is a named constant with a descriptive Javadoc
 *   - Parameters are documented inline with comments
 * Naming convention:
 *   <DOMAIN>_<OPERATION>  e.g. USER_DELETE, FOLLOW_CREATE, RECOMMEND_COUNT
 */
public final class CypherQueries {
    private CypherQueries() {}

    public static final String USER_DELETE = """
			MATCH (u:UserProfile {id: $profileId})
			DETACH DELETE u
			""";

    /* Params: $profileIds (List<String>) */
    public static final String USER_BATCH_COUNTS =
            """
			MATCH (u:UserProfile) WHERE u.id IN $profileIds
			RETURN u.id AS id,
				COUNT { (u)<-[:FOLLOW]-() } AS followerCount,
				COUNT { (u)-[:FOLLOW]->() } AS followingCount
			""";

    /* Params: $profileId (String), $topicIds (List<String>) */
    public static final String USER_REPLACE_INTERESTS =
            """
			MATCH (u:UserProfile {id: $profileId})
			OPTIONAL MATCH (u)-[r:INTERESTED_IN]->(:Topic)
			DELETE r
			WITH u
			UNWIND $topicIds AS tid
			MERGE (t:Topic {id: tid})
			MERGE (u)-[:INTERESTED_IN]->(t)
			""";

    /* Params: $profileId (String), $role (String) */
    public static final String USER_ADD_ROLE =
            """
			MATCH (u:UserProfile {id: $profileId})
			SET u.roles = CASE
				WHEN $role IN u.roles THEN u.roles
				ELSE u.roles + $role
			END
			""";

    /* Params: $profileId (String), $subjectIds (List<String>) */
    public static final String TUTOR_REPLACE_SUBJECTS =
            """
			MATCH (u:UserProfile {id: $profileId})
			OPTIONAL MATCH (u)-[r:TEACHES]->()
			DELETE r
			WITH u
			UNWIND $subjectIds AS subId
			MERGE (s:Subject {id: subId})
			MERGE (u)-[:TEACHES]->(s)
			""";

    /* Params: $followerId (String), $followeeId (String) */
    public static final String FOLLOW_CREATE =
            """
			MATCH (follower:UserProfile {id: $followerId})
			MATCH (followee:UserProfile {id: $followeeId})
			MERGE (follower)-[:FOLLOW]->(followee)
			""";

    /* Params: $followerId (String), $followeeId (String) */
    public static final String FOLLOW_DELETE =
            """
			MATCH (follower:UserProfile {id: $followerId})
				-[r:FOLLOW]->
				(followee:UserProfile {id: $followeeId})
			DELETE r
			""";

    /* Params: $profileId (String)
     *  Returns: totalElements (Long) */
    public static final String RECOMMEND_COUNT =
            """
			CALL {
				WITH $profileId AS pid
				MATCH (a:UserProfile {id: pid})-[:FOLLOW]->(b:UserProfile)-[:FOLLOW]->(c:UserProfile)
				WHERE a <> c AND NOT (a)-[:FOLLOW]->(c)
				RETURN c

				UNION ALL

				WITH $profileId AS pid
				MATCH (a:UserProfile {id: pid})-[:ENROLLED_IN]->(cr:ClassRoom)<-[:ENROLLED_IN]-(c:UserProfile)
				WHERE a <> c AND NOT (a)-[:FOLLOW]->(c)
				  AND (cr.status IS NULL OR NOT cr.status IN ['COMPLETED', 'CANCELLED'])
				RETURN c

				UNION ALL

				WITH $profileId AS pid
				MATCH (a:UserProfile {id: pid})-[:STUDY_AT]->(:University)<-[:STUDY_AT]-(c:UserProfile)
				WHERE a <> c AND NOT (a)-[:FOLLOW]->(c)
				RETURN c
			}
			RETURN count(DISTINCT c) AS totalElements
			""";

    /**
     * Fetch recommended profile IDs sorted by relevance score, with pagination.
     * Params:
     *   $profileId   (String)
     *   $mutualScore (Integer) — score for mutual follow
     *   $classScore  (Integer) — score for same classroom
     *   $uniScore    (Integer) — score for same university
     *   $skip        (Integer)
     *   $limit       (Integer)
     * Returns: recommendedIds (List<String>)
     */
    public static final String RECOMMEND_IDS =
            """
			CALL {
				WITH $profileId AS pid
				MATCH (a:UserProfile {id: pid})-[:FOLLOW]->(b:UserProfile)-[:FOLLOW]->(c:UserProfile)
				WHERE a <> c AND NOT (a)-[:FOLLOW]->(c)
				RETURN c, $mutualScore AS score

				UNION ALL

				WITH $profileId AS pid
				MATCH (a:UserProfile {id: pid})-[:ENROLLED_IN]->(cr:ClassRoom)<-[:ENROLLED_IN]-(c:UserProfile)
				WHERE a <> c AND NOT (a)-[:FOLLOW]->(c)
				  AND (cr.status IS NULL OR NOT cr.status IN ['COMPLETED', 'CANCELLED'])
				RETURN c, $classScore AS score

				UNION ALL

				WITH $profileId AS pid
				MATCH (a:UserProfile {id: pid})-[:STUDY_AT]->(:University)<-[:STUDY_AT]-(c:UserProfile)
				WHERE a <> c AND NOT (a)-[:FOLLOW]->(c)
				RETURN c, $uniScore AS score
			}
			WITH c, sum(score) AS totalScore
			ORDER BY totalScore DESC
			SKIP $skip
			LIMIT $limit
			RETURN collect(c.id) AS recommendedIds
			""";

    /* Params: $data (List<Map>) — each map: {id, name} */

    public static final String SYNC_SUBJECTS =
            """
			UNWIND $data AS row
			MERGE (s:Subject {id: row.id})
			SET s.name = row.name
			""";

    /* Params: $data (List<Map>) — each map: {id, name, subjectId} */
    public static final String SYNC_TOPICS =
            """
			UNWIND $data AS row
			MERGE (t:Topic {id: row.id})
			SET t.name = row.name
			WITH t, row
			MATCH (s:Subject {id: row.subjectId})
			MERGE (t)-[:BELONGS_TO]->(s)
			""";
}
