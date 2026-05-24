package vn.edu.hcmut.profile.service;

import java.util.*;
import java.util.stream.Collectors;

import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import vn.edu.hcmut.profile.constant.CypherQueries;
import vn.edu.hcmut.profile.dto.request.ProfileCreationRequest;
import vn.edu.hcmut.profile.entity.jpa.University;
import vn.edu.hcmut.profile.entity.neo4j.UniversityNode;
import vn.edu.hcmut.profile.entity.neo4j.UserProfileNode;
import vn.edu.hcmut.profile.exception.AppException;
import vn.edu.hcmut.profile.exception.ErrorCode;
import vn.edu.hcmut.profile.repository.UserProfileNodeRepository;

@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ProfileNeo4jService {
    UserProfileNodeRepository neo4jRepository;
    Neo4jClient neo4jClient;

    @Transactional
    public void createUserNode(String profileId, ProfileCreationRequest request, University university) {
        UniversityNode uniNode = toUniversityNode(university);

        UserProfileNode userNode = UserProfileNode.builder()
                .id(profileId)
                .fullName(request.getFirstName() + " " + request.getLastName())
                .roles(List.of("STUDENT"))
                .university(uniNode)
                .build();

        try {
            neo4jRepository.save(userNode);
            log.info("Created UserProfile node in Neo4j for profile {}", profileId);
        } catch (Exception e) {
            log.error("Error saving UserProfile node to Neo4j for profile {}", profileId, e);
            throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION);
        }
    }

    @Transactional
    public void updateUserUniversity(String profileId, University newUniversity) {
        neo4jRepository.findById(profileId).ifPresent(node -> {
            node.setUniversity(toUniversityNode(newUniversity));
            node.setNotNew();
            neo4jRepository.save(node);
            log.info("Updated university for profile {} in Neo4j", profileId);
        });
    }

    @Transactional
    public void deleteUserNode(String profileId) {
        neo4jClient
                .query(CypherQueries.USER_DELETE)
                .bindAll(Map.of("profileId", profileId))
                .run();
        log.info("Deleted UserProfile node {} and all relationships from Neo4j", profileId);
    }

    @Transactional
    public void addRole(String profileId, String role) {
        neo4jClient
                .query(CypherQueries.USER_ADD_ROLE)
                .bindAll(Map.of(
                        "profileId", profileId,
                        "role", role))
                .run();

        log.info("Added role '{}' to profile '{}' via direct Cypher execution", role, profileId);
    }

    @Transactional
    public void removeRole(String profileId, String role) {
        UserProfileNode node =
                neo4jRepository.findById(profileId).orElseThrow(() -> new AppException(ErrorCode.PROFILE_NOT_FOUND));

        if (node.getRoles() != null && node.getRoles().remove(role)) {
            neo4jRepository.save(node);
            log.info("Removed role '{}' from profile '{}'", role, profileId);
        } else {
            log.info("Profile '{}' does not have role '{}', skipping", profileId, role);
        }
    }

    @Transactional
    public void updateTutorSubjects(String profileId, Set<String> subjectIds) {
        neo4jClient
                .query(CypherQueries.TUTOR_REPLACE_SUBJECTS)
                .bindAll(Map.of(
                        "profileId", profileId, "subjectIds", subjectIds != null ? subjectIds : Collections.emptySet()))
                .run();
        log.info(
                "Updated {} TEACHES relationships for profile {}",
                subjectIds != null ? subjectIds.size() : 0,
                profileId);
    }

    @Transactional
    public void updateUserInterests(String profileId, List<String> topicIds) {
        neo4jClient
                .query(CypherQueries.USER_REPLACE_INTERESTS)
                .bindAll(Map.of(
                        "profileId", profileId, "topicIds", topicIds != null ? topicIds : Collections.emptyList()))
                .run();
        log.info(
                "Updated {} INTERESTED_IN relationships for profile {}",
                topicIds != null ? topicIds.size() : 0,
                profileId);
    }

    public List<String> getUserInterests(String profileId) {
        String query = "MATCH (u {id: $profileId})-[:INTERESTED_IN]->(t) RETURN t.id AS topicId";

        return neo4jClient
                .query(query)
                .bindAll(Map.of("profileId", profileId))
                .fetchAs(String.class)
                .mappedBy((typeSystem, record) -> record.get("topicId").asString())
                .all()
                .stream()
                .toList();
    }

    public int countFollowers(String profileId) {
        Integer count = neo4jRepository.countFollowers(profileId);
        return count != null ? count : 0;
    }

    public int countFollowing(String profileId) {
        Integer count = neo4jRepository.countFollowing(profileId);
        return count != null ? count : 0;
    }

    public Map<String, Map<String, Integer>> getBatchCounts(List<String> profileIds) {
        if (profileIds == null || profileIds.isEmpty()) return Collections.emptyMap();

        return neo4jClient
                .query(CypherQueries.USER_BATCH_COUNTS)
                .bind(profileIds)
                .to("profileIds")
                .fetchAs(Map.class)
                .mappedBy((typeSystem, record) -> {
                    Map<String, Integer> counts = new HashMap<>();
                    counts.put("followerCount", record.get("followerCount").asInt());
                    counts.put("followingCount", record.get("followingCount").asInt());

                    Map<String, Object> result = new HashMap<>();
                    result.put("id", record.get("id").asString());
                    result.put("counts", counts);
                    return result;
                })
                .all()
                .stream()
                .collect(Collectors.toMap(
                        m -> (String) m.get("id"), m -> (Map<String, Integer>) m.get("counts"), (a, b) -> a));
    }

    private UniversityNode toUniversityNode(University university) {
        return UniversityNode.builder()
                .id(university.getId())
                .name(university.getName())
                .abbreviation(university.getAbbreviation())
                .build();
    }
}
