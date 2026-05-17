package vn.edu.hcmut.profile.service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import vn.edu.hcmut.event.FollowNotificationEvent;
import vn.edu.hcmut.profile.constant.CypherQueries;
import vn.edu.hcmut.profile.dto.response.PageResponse;
import vn.edu.hcmut.profile.dto.response.ProfileResponse;
import vn.edu.hcmut.profile.entity.jpa.University;
import vn.edu.hcmut.profile.entity.jpa.UserProfile;
import vn.edu.hcmut.profile.entity.neo4j.UserProfileNode;
import vn.edu.hcmut.profile.exception.AppException;
import vn.edu.hcmut.profile.exception.ErrorCode;
import vn.edu.hcmut.profile.mapper.ProfileMapper;
import vn.edu.hcmut.profile.repository.UniversityRepository;
import vn.edu.hcmut.profile.repository.UserProfileNodeRepository;
import vn.edu.hcmut.profile.repository.UserProfileRepository;

@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class FollowService {
    UserProfileNodeRepository neo4jRepository;
    UserProfileRepository jpaRepository;
    UniversityRepository universityRepository;
    Neo4jClient neo4jClient;
    ProfileMapper profileMapper;
    ProfileNeo4jService profileNeo4jService;

    KafkaTemplate<String, Object> kafkaTemplate;

    @Transactional
    public void followProfile(String followerId, String followeeId) {
        if (followerId.equals(followeeId)) {
            throw new AppException(ErrorCode.CANNOT_FOLLOW_YOURSELF);
        }

        UserProfile follower =
                jpaRepository.findById(followerId).orElseThrow(() -> new AppException(ErrorCode.PROFILE_NOT_FOUND));

        if (!jpaRepository.existsById(followeeId)) {
            throw new AppException(ErrorCode.PROFILE_NOT_FOUND);
        }

        neo4jClient
                .query(CypherQueries.FOLLOW_CREATE)
                .bindAll(Map.of("followerId", followerId, "followeeId", followeeId))
                .run();

        log.info("Profile {} started following Profile {}", followerId, followeeId);

        String followerName = (follower.getLastName() != null ? follower.getLastName() : "") + " "
                + (follower.getFirstName() != null ? follower.getFirstName() : "");

        FollowNotificationEvent event = FollowNotificationEvent.builder()
                .followerId(followerId)
                .followerName(followerName.trim())
                .followeeId(followeeId)
                .action("FOLLOWED")
                .build();

        kafkaTemplate.send("follow-events", event);
    }

    @Transactional
    public void unfollowProfile(String followerId, String followeeId) {
        neo4jClient
                .query(CypherQueries.FOLLOW_DELETE)
                .bindAll(Map.of("followerId", followerId, "followeeId", followeeId))
                .run();

        log.info("Profile {} unfollowed Profile {}", followerId, followeeId);
    }

    @Transactional(readOnly = true)
    public PageResponse<ProfileResponse> getFollowers(String profileId, int page, int size) {
        Pageable pageable = toPageable(page, size);
        Page<UserProfileNode> nodes = neo4jRepository.findFollowers(profileId, pageable);
        return toPageResponse(nodes, page);
    }

    @Transactional(readOnly = true)
    public PageResponse<ProfileResponse> getFollowing(String profileId, int page, int size) {
        Pageable pageable = toPageable(page, size);
        Page<UserProfileNode> nodes = neo4jRepository.findFollowing(profileId, pageable);
        return toPageResponse(nodes, page);
    }

    private PageResponse<ProfileResponse> toPageResponse(Page<UserProfileNode> nodes, int page) {
        List<String> ids =
                nodes.getContent().stream().map(UserProfileNode::getId).toList();

        // Single batch JPA fetch
        Map<String, UserProfile> profileMap =
                jpaRepository.findAllById(ids).stream().collect(Collectors.toMap(UserProfile::getId, p -> p));

        List<UserProfile> users = ids.stream()
                .map(profileMap::get)
                .filter(Objects::nonNull)
                .toList();

        List<ProfileResponse> responses = users.stream()
                .map(profileMapper::toProfileResponse)
                .toList();

        List<Integer> uniIds = users.stream().map(UserProfile::getUniversityId).filter(Objects::nonNull).distinct().toList();
        if (!uniIds.isEmpty()) {
            Map<Integer, String> uniMap = universityRepository.findAllById(uniIds).stream()
                    .collect(Collectors.toMap(University::getId, University::getName));
            for (int i = 0; i < users.size(); i++) {
                if (users.get(i).getUniversityId() != null) {
                    responses.get(i).setUniversity(uniMap.get(users.get(i).getUniversityId()));
                }
            }
        }

        // Batch fetch follower/following counts from Neo4j
        Map<String, Map<String, Integer>> countsMap = profileNeo4jService.getBatchCounts(ids);
        responses.forEach(p -> {
            Map<String, Integer> counts = countsMap.get(p.getId());
            if (counts != null) {
                p.setFollowerCount(counts.getOrDefault("followerCount", 0));
                p.setFollowingCount(counts.getOrDefault("followingCount", 0));
            } else {
                p.setFollowerCount(0);
                p.setFollowingCount(0);
            }
        });

        return PageResponse.<ProfileResponse>builder()
                .currentPage(page)
                .totalPages(nodes.getTotalPages())
                .pageSize(nodes.getSize())
                .totalElements(nodes.getTotalElements())
                .data(responses)
                .build();
    }

    private Pageable toPageable(int page, int size) {
        return PageRequest.of((page > 0) ? page - 1 : 0, size);
    }
}
