package vn.edu.hcmut.profile.service;

import java.util.*;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import vn.edu.hcmut.event.dto.ProfileUpdatedEvent;
import vn.edu.hcmut.profile.dto.request.ProfileCreationRequest;
import vn.edu.hcmut.profile.dto.request.ProfileUpdateRequest;
import vn.edu.hcmut.profile.dto.response.PageResponse;
import vn.edu.hcmut.profile.dto.response.ProfileResponse;
import vn.edu.hcmut.profile.entity.jpa.University;
import vn.edu.hcmut.profile.entity.jpa.UserProfile;
import vn.edu.hcmut.profile.entity.neo4j.UniversityNode;
import vn.edu.hcmut.profile.entity.neo4j.UserProfileNode;
import vn.edu.hcmut.profile.exception.AppException;
import vn.edu.hcmut.profile.exception.ErrorCode;
import vn.edu.hcmut.profile.mapper.ProfileMapper;
import vn.edu.hcmut.profile.repository.UniversityRepository;
import vn.edu.hcmut.profile.repository.UserProfileNodeRepository;
import vn.edu.hcmut.profile.repository.UserProfileRepository;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class ProfileService {

    UserProfileRepository jpaRepository;
    UserProfileNodeRepository neo4jRepository;
    UniversityRepository universityRepository;

    ProfileMapper profileMapper;

    Neo4jClient neo4jClient;

    KafkaTemplate<String, ProfileUpdatedEvent> kafkaTemplate;

    /**
     * Creates a profile in both JPA and Neo4j
     * Uses the same UUID to ensure 1-1 mapping between the two databases.
     */
    @Transactional(transactionManager = "transactionManager", rollbackFor = Exception.class)
    public ProfileResponse createProfile(ProfileCreationRequest request) {
        String profileId = UUID.randomUUID().toString();

        University university = universityRepository
                .findById(request.getUniversityId())
                .orElseThrow(() -> new AppException(ErrorCode.UNIVERSITY_NOT_FOUND));

        // Save to JPA
        UserProfile user = profileMapper.toProfile(request);
        user.setId(profileId);
        user.setUniversityId(university.getId());
        jpaRepository.save(user);

        // Save to Neo4j
        UniversityNode uniNode = UniversityNode.builder()
                .id(university.getId())
                .name(university.getName())
                .abbreviation(university.getAbbreviation())
                .build();

        UserProfileNode userNode = UserProfileNode.builder()
                .id(profileId)
                .fullName(request.getFirstName() + " " + request.getLastName())
                .roles(List.of("STUDENT")) // Default role
                .university(uniNode)
                .build();

        try {
            neo4jRepository.save(userNode);
        } catch (Exception e) {
            log.error("Error saving to Neo4j for profile: {}", profileId, e);
            throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION);
        }

        var response = profileMapper.toProfileResponse(user);
        response.setUniversity(university.getName());
        response.setFollowerCount(0);
        response.setFollowingCount(0);
        return response;
    }

    public ProfileResponse getProfile(String id) {
        UserProfile user = jpaRepository.findById(id).orElseThrow(() -> new AppException(ErrorCode.PROFILE_NOT_FOUND));
        return toProfileResponse(user);
    }

    public ProfileResponse getProfileByAccountId(String accountId) {
        UserProfile user = jpaRepository
                .findByAccountId(accountId)
                .orElseThrow(() -> new AppException(ErrorCode.PROFILE_NOT_FOUND));

        return toProfileResponse(user);
    }

    public ProfileResponse getMyProfile() {
        String accountId = getCurrentAccountId();
        return getProfileByAccountId(accountId);
    }

    public List<ProfileResponse> searchProfile(String keyword, int limit) {
        if (keyword == null || keyword.trim().isEmpty()) return List.of();

        Pageable pageable = PageRequest.of(0, limit);

        return jpaRepository.search(keyword.trim(), pageable).getContent().stream()
                .map(profileMapper::toProfileResponse)
                .toList();
    }

    /**
     * Updates profile data. If university changes, syncs the update to Neo4j.
     */
    @Transactional(transactionManager = "transactionManager", rollbackFor = Exception.class)
    public ProfileResponse updateProfile(ProfileUpdateRequest request) {
        String accountId = getCurrentAccountId();

        UserProfile user = jpaRepository
                .findByAccountId(accountId)
                .orElseThrow(() -> new AppException(ErrorCode.PROFILE_NOT_FOUND));

        profileMapper.updateProfile(user, request);

        University finalUni = null;

        // Handle university update and cross-DB synchronization
        if (request.getUniversityId() != null && !request.getUniversityId().equals(user.getUniversityId())) {
            finalUni = universityRepository
                    .findById(request.getUniversityId())
                    .orElseThrow(() -> new AppException(ErrorCode.UNIVERSITY_NOT_FOUND));

            user.setUniversityId(finalUni.getId());

            final University universityNeo4j = finalUni;
            neo4jRepository.findById(user.getId()).ifPresent(node -> {
                UniversityNode uniNode = UniversityNode.builder()
                        .id(universityNeo4j.getId())
                        .name(universityNeo4j.getName())
                        .abbreviation(universityNeo4j.getAbbreviation())
                        .build();

                node.setUniversity(uniNode);
                node.setNotNew();
                neo4jRepository.save(node);
            });
        }

        jpaRepository.save(user);

        var response = profileMapper.toProfileResponse(user);

        // Attach university name to response
        if (finalUni != null) {
            response.setUniversity(finalUni.getName());
        } else if (user.getUniversityId() != null) {
            universityRepository
                    .findById(user.getUniversityId())
                    .ifPresent(university -> response.setUniversity(university.getName()));
        }

        ProfileUpdatedEvent event = ProfileUpdatedEvent.builder()
                .profileId(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .avatar(user.getAvatarUrl())
                .build();

        kafkaTemplate.send("profile-events", event.getProfileId(), event);

        return response;
    }

    @Transactional(transactionManager = "transactionManager", rollbackFor = Exception.class)
    public void deleteProfile(String profileId) {
        UserProfile user =
                jpaRepository.findById(profileId).orElseThrow(() -> new AppException(ErrorCode.PROFILE_NOT_FOUND));

        jpaRepository.delete(user);

        // Delete from Neo4j natively via Cypher
        String query = """
				MATCH (u:UserProfile {id: $profileId})
				DETACH DELETE u
				""";
        neo4jClient.query(query).bindAll(Map.of("profileId", profileId)).run();

        log.info("Deleted UserProfile {} and all its relationships in Neo4j", profileId);
    }

    public void addRole(String profileId, String role) {
        neo4jRepository.findById(profileId).ifPresent(node -> {
            List<String> currentRoles = node.getRoles();

            if (currentRoles == null) currentRoles = new ArrayList<>();

            if (!currentRoles.contains(role)) {
                currentRoles.add(role);
                node.setRoles(currentRoles);
                node.setNotNew();
                neo4jRepository.save(node);
            }
        });
    }

    @Transactional
    public void removeRole(String profileId, String role) {
        UserProfileNode profile =
                neo4jRepository.findById(profileId).orElseThrow(() -> new AppException(ErrorCode.PROFILE_NOT_FOUND));

        if (profile.getRoles() != null && profile.getRoles().contains(role)) {
            profile.getRoles().remove(role);
            neo4jRepository.save(profile);
            log.info("Successfully removed role '{}' from profile '{}'", role, profileId);
        } else {
            log.info("Profile '{}' does not have role '{}', skipping removal", profileId, role);
        }
    }

    /**
     * BATCH GET: Retrieves multiple profiles at once without pagination.
     * Used primarily by other microservices (via FeignClient) to aggregate data.
     */
    public List<ProfileResponse> getProfilesByIds(List<String> profileIds) {
        return jpaRepository.findAllById(profileIds).stream()
                .map(profileMapper::toProfileResponse)
                .toList();
    }

    /**
     * Retrieves a paginated list of users who are following the given profile.
     */
    public PageResponse<ProfileResponse> getFollowers(String profileId, int page, int size) {
        Pageable pageable = PageRequest.of((page > 0) ? page - 1 : 0, size);

        // Fetch relations from Neo4j (Paginated)
        Page<UserProfileNode> profile = neo4jRepository.findFollowers(profileId, pageable);

        // Extract IDs
        List<String> followerIds =
                profile.getContent().stream().map(UserProfileNode::getId).toList();

        // Fetch detailed data from JPA
        Map<String, UserProfile> followers =
                jpaRepository.findAllById(followerIds).stream().collect(Collectors.toMap(UserProfile::getId, p -> p));

        List<ProfileResponse> responses = followerIds.stream()
                .map(followers::get)
                .filter(Objects::nonNull) // prevent null pointer
                .map(profileMapper::toProfileResponse)
                .toList();

        return PageResponse.<ProfileResponse>builder()
                .currentPage(page)
                .totalPages(profile.getTotalPages())
                .pageSize(profile.getSize())
                .totalElements(profile.getTotalElements())
                .data(responses)
                .build();
    }

    /**
     * Retrieves a paginated list of users that the given profile is currently following.
     */
    public PageResponse<ProfileResponse> getFollowing(String profileId, int page, int size) {
        Pageable pageable = PageRequest.of((page > 0) ? page - 1 : 0, size);

        // Fetch relations from Neo4j (Paginated)
        Page<UserProfileNode> profile = neo4jRepository.findFollowing(profileId, pageable);

        // Extract IDs
        List<String> followingIds =
                profile.getContent().stream().map(UserProfileNode::getId).toList();

        // Fetch detailed data from JPA
        Map<String, UserProfile> followings =
                jpaRepository.findAllById(followingIds).stream().collect(Collectors.toMap(UserProfile::getId, p -> p));

        List<ProfileResponse> responses = followingIds.stream()
                .map(followings::get)
                .filter(Objects::nonNull)
                .map(profileMapper::toProfileResponse)
                .toList();

        return PageResponse.<ProfileResponse>builder()
                .currentPage(page)
                .totalPages(profile.getTotalPages())
                .pageSize(profile.getSize())
                .totalElements(profile.getTotalElements())
                .data(responses)
                .build();
    }

    /**
     * Create a follow relationship between the two profiles
     */
    @Transactional(transactionManager = "transactionManager", rollbackFor = Exception.class)
    public void followProfile(String followerId, String followeeId) {
        if (followerId.equals(followeeId)) {
            throw new AppException(ErrorCode.CANNOT_FOLLOW_YOURSELF);
        }

        if (!jpaRepository.existsById(followerId) || !jpaRepository.existsById(followeeId)) {
            throw new AppException(ErrorCode.PROFILE_NOT_FOUND);
        }

        String query =
                """
				MATCH (follower:UserProfile {id: $followerId})
				MATCH (followee:UserProfile {id: $followeeId})
				MERGE (follower)-[:FOLLOW]->(followee)
				""";

        neo4jClient
                .query(query)
                .bindAll(Map.of(
                        "followerId", followerId,
                        "followeeId", followeeId))
                .run();

        log.info("Profile {} started following Profile {}", followerId, followeeId);
    }

    /**
     * Remove follow relationship between the two profiles.
     */
    @Transactional(transactionManager = "transactionManager", rollbackFor = Exception.class)
    public void unfollowProfile(String followerId, String followeeId) {
        String query =
                """
				MATCH (follower:UserProfile {id: $followerId})-[r:FOLLOW]->(followee:UserProfile {id: $followeeId})
				DELETE r
				""";

        neo4jClient
                .query(query)
                .bindAll(Map.of(
                        "followerId", followerId,
                        "followeeId", followeeId))
                .run();

        log.info("Profile {} unfollowed Profile {}", followerId, followeeId);
    }

    @Transactional
    public void updateTutorSubjects(String profileId, Set<String> subjectIds) {
        String query =
                """
				MATCH (u:UserProfile {id: $profileId})
				OPTIONAL MATCH (u)-[r:TEACHES]->()
				DELETE r
				WITH u
				UNWIND $subjectIds AS subId

				MERGE (s:Subject {id: subId})
				MERGE (u)-[:TEACHES]->(s)
				""";

        neo4jClient
                .query(query)
                .bindAll(Map.of(
                        "profileId", profileId, "subjectIds", subjectIds != null ? subjectIds : Collections.emptySet()))
                .run();
    }

    public PageResponse<ProfileResponse> getPeopleYouMayKnow(String profileId, int page, int size) {
        int actualPage = Math.max(0, page - 1);
        int skip = actualPage * size;
        int limit = Math.max(1, size);

        String countQuery =
                """
			CALL {
				WITH $profileId AS pid
				MATCH (a:UserProfile {id: pid})-[:FOLLOW]->(b:UserProfile)-[:FOLLOW]->(c:UserProfile)
				WHERE a <> c AND NOT (a)-[:FOLLOW]->(c)
				RETURN c

				UNION ALL

				WITH $profileId AS pid
				MATCH (a:UserProfile {id: pid})-[:ENROLLED_IN]->(:ClassRoom)<-[:ENROLLED_IN]-(c:UserProfile)
				WHERE a <> c AND NOT (a)-[:FOLLOW]->(c)
				RETURN c

				UNION ALL

				WITH $profileId AS pid
				MATCH (a:UserProfile {id: pid})-[:STUDY_AT]->(:University)<-[:STUDY_AT]-(c:UserProfile)
				WHERE a <> c AND NOT (a)-[:FOLLOW]->(c)
				RETURN c
			}
			RETURN count(DISTINCT c) AS totalElements
			""";

        String idsQuery =
                """
			CALL {
				WITH $profileId AS pid
				MATCH (a:UserProfile {id: pid})-[:FOLLOW]->(b:UserProfile)-[:FOLLOW]->(c:UserProfile)
				WHERE a <> c AND NOT (a)-[:FOLLOW]->(c)
				RETURN c, 5 AS score

				UNION ALL

				WITH $profileId AS pid
				MATCH (a:UserProfile {id: pid})-[:ENROLLED_IN]->(:ClassRoom)<-[:ENROLLED_IN]-(c:UserProfile)
				WHERE a <> c AND NOT (a)-[:FOLLOW]->(c)
				RETURN c, 3 AS score

				UNION ALL

				WITH $profileId AS pid
				MATCH (a:UserProfile {id: pid})-[:STUDY_AT]->(:University)<-[:STUDY_AT]-(c:UserProfile)
				WHERE a <> c AND NOT (a)-[:FOLLOW]->(c)
				RETURN c, 1 AS score
			}

			WITH c, sum(score) AS totalScore
			ORDER BY totalScore DESC
			SKIP $skip
			LIMIT $limit

			RETURN collect(c.id) AS recommendedIds
			""";

        try {
            Map<String, Object> params = Map.of(
                    "profileId", profileId,
                    "skip", skip,
                    "limit", limit);

            long totalElements = 0;
            Optional<Map<String, Object>> countResult =
                    neo4jClient.query(countQuery).bindAll(params).fetch().one();
            if (countResult.isPresent() && countResult.get().get("totalElements") != null) {
                totalElements = ((Number) countResult.get().get("totalElements")).longValue();
            }

            Optional<Map<String, Object>> result =
                    neo4jClient.query(idsQuery).bindAll(params).fetch().one();

            List<String> followerIds = new ArrayList<>();
            if (result.isPresent() && result.get().get("recommendedIds") != null) {
                followerIds = (List<String>) result.get().get("recommendedIds");
            }

            if (followerIds.isEmpty()) {
                return PageResponse.<ProfileResponse>builder()
                        .currentPage(page)
                        .totalPages(0)
                        .pageSize(size)
                        .totalElements(0)
                        .data(Collections.emptyList())
                        .build();
            }

            Map<String, UserProfile> userProfilesMap = jpaRepository.findAllById(followerIds).stream()
                    .collect(Collectors.toMap(UserProfile::getId, p -> p));

            List<ProfileResponse> responses = followerIds.stream()
                    .map(userProfilesMap::get)
                    .filter(Objects::nonNull)
                    .map(this::toProfileResponse)
                    .toList();

            int totalPages = (int) Math.ceil((double) totalElements / size);

            return PageResponse.<ProfileResponse>builder()
                    .currentPage(page)
                    .totalPages(totalPages)
                    .pageSize(size)
                    .totalElements(totalElements)
                    .data(responses)
                    .build();

        } catch (Exception e) {
            log.error("Lỗi lấy gợi ý kết bạn từ Neo4j: {}", e.getMessage());
            return PageResponse.<ProfileResponse>builder()
                    .currentPage(page)
                    .totalPages(0)
                    .pageSize(size)
                    .totalElements(0)
                    .data(Collections.emptyList())
                    .build();
        }
    }

    private String getCurrentAccountId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        return authentication.getName();
    }

    private ProfileResponse toProfileResponse(UserProfile user) {
        ProfileResponse response = profileMapper.toProfileResponse(user);

        if (user.getUniversityId() != null) {
            universityRepository
                    .findById(user.getUniversityId())
                    .ifPresent(uni -> response.setUniversity(uni.getName()));
        }

        Integer followerCount = neo4jRepository.countFollowers(user.getId());
        Integer followingCount = neo4jRepository.countFollowing(user.getId());

        response.setFollowerCount(followerCount != null ? followerCount : 0);
        response.setFollowingCount(followingCount != null ? followingCount : 0);

        return response;
    }
}
