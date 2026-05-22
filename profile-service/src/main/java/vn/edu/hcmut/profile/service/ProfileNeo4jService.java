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
import vn.edu.hcmut.profile.dto.request.ProfileUpdateRequest;
import vn.edu.hcmut.profile.entity.jpa.University;
import vn.edu.hcmut.profile.entity.neo4j.UniversityNode;
import vn.edu.hcmut.profile.entity.neo4j.UserProfileNode;
import vn.edu.hcmut.profile.entity.records.FollowCounts;
import vn.edu.hcmut.profile.entity.records.ProfileFollowCountRow;
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

    @Transactional(transactionManager = "neo4jTransactionManager")
    public void createUserNode(String profileId, ProfileCreationRequest request, University university) {

        if (neo4jRepository.existsById(profileId)) {
            log.info("User profile node with id {} already exists in Neo4j. Skipping creation.", profileId);
            return;
        }

        UniversityNode uni = university != null ? toUniversityNode(university) : null;

        UserProfileNode user = UserProfileNode.builder()
                .id(profileId)
                .fullName(fullName(request.getFirstName(), request.getLastName()))
                .roles(List.of("USER"))
                .university(uni)
                .build();

        try {
            neo4jRepository.save(user);
            log.info("Successfully created user profile node in Neo4j for profile {}", profileId);
        } catch (Exception e) {
            log.error("Error saving user profile node to Neo4j during sync for profile {}", profileId, e);
            throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION);
        }
    }

    @Transactional(transactionManager = "neo4jTransactionManager")
    public void updateUserNode(String profileId, ProfileUpdateRequest request, University newUniversity) {
        neo4jRepository.findById(profileId).ifPresent(node -> {
            if (request.getFirstName() != null || request.getLastName() != null) {
                node.setFullName(fullName(request.getFirstName(), request.getLastName()));
            }

            if (newUniversity != null) node.setUniversity(toUniversityNode(newUniversity));
            node.setNotNew();

            neo4jRepository.save(node);
            log.info("Updated basic profile information for profile {} in Neo4j", profileId);
        });
    }

    @Transactional(transactionManager = "neo4jTransactionManager")
    public void deleteUserNode(String profileId) {
        neo4jClient
                .query(CypherQueries.USER_DELETE)
                .bindAll(Map.of("profileId", profileId))
                .run();
        log.info("Deleted UserProfile node {} and all relationships from Neo4j", profileId);
    }

    @Transactional(transactionManager = "neo4jTransactionManager")
    public void addRole(String profileId, String role) {
        neo4jClient
                .query(CypherQueries.USER_ADD_ROLE)
                .bindAll(Map.of(
                        "profileId", profileId,
                        "role", role))
                .run();

        log.info("Added role '{}' to profile '{}' via direct Cypher execution", role, profileId);
    }

    @Transactional(transactionManager = "neo4jTransactionManager")
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

    @Transactional(transactionManager = "neo4jTransactionManager")
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

    @Transactional(transactionManager = "neo4jTransactionManager")
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

    @Transactional(readOnly = true, transactionManager = "neo4jTransactionManager")
    public Map<String, FollowCounts> getBatchCounts(List<String> profileIds) {
        if (profileIds == null || profileIds.isEmpty()) return Collections.emptyMap();

        return neo4jClient
                .query(CypherQueries.USER_BATCH_COUNTS)
                .bind(profileIds)
                .to("profileIds")
                .fetchAs(ProfileFollowCountRow.class)
                .mappedBy((typeSystem, record) -> new ProfileFollowCountRow(
                        record.get("id").asString(),
                        new FollowCounts(
                                record.get("followerCount").asInt(),
                                record.get("followingCount").asInt()
                        )
                ))
                .all()
                .stream()
                .collect(Collectors.toMap(
                        ProfileFollowCountRow::id,
                        ProfileFollowCountRow::counts,
                        (a, b) -> a
                ));
    }

    @Transactional(transactionManager = "neo4jTransactionManager")
    public void createFollowRelationship(String followerId, String followeeId) {
        neo4jClient
                .query(CypherQueries.FOLLOW_CREATE)
                .bindAll(Map.of("followerId", followerId, "followeeId", followeeId))
                .run();
        log.info("Neo4j Sync: Profile {} followed Profile {}", followerId, followeeId);
    }

    @Transactional(transactionManager = "neo4jTransactionManager")
    public void removeFollowRelationship(String followerId, String followeeId) {
        neo4jClient
                .query(CypherQueries.FOLLOW_DELETE)
                .bindAll(Map.of("followerId", followerId, "followeeId", followeeId))
                .run();
        log.info("Neo4j Sync: Profile {} unfollowed Profile {}", followerId, followeeId);
    }

    private UniversityNode toUniversityNode(University university) {
        return UniversityNode.builder()
                .id(university.getId())
                .name(university.getName())
                .abbreviation(university.getAbbreviation())
                .build();
    }

    private String fullName(String firstName, String lastName) {
        return ((firstName != null ? firstName : "") + " " + (lastName != null ? lastName : "")).trim();
    }
}
