package vn.edu.hcmut.profile.service;

import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import vn.edu.hcmut.profile.dto.response.PageResponse;
import vn.edu.hcmut.profile.dto.response.ProfileResponse;
import vn.edu.hcmut.profile.entity.jpa.UserProfile;
import vn.edu.hcmut.profile.entity.neo4j.UserProfileNode;
import vn.edu.hcmut.profile.exception.AppException;
import vn.edu.hcmut.profile.exception.ErrorCode;
import vn.edu.hcmut.profile.repository.UserProfileNodeRepository;
import vn.edu.hcmut.profile.repository.UserProfileRepository;
import vn.edu.hcmut.profile.service.assembler.ProfileResponseAssembler;
import vn.edu.hcmut.profile.service.outbox.OutboxEventService;

@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class FollowService {

    UserProfileRepository jpaRepository;
    UserProfileNodeRepository neo4jRepository;

    ProfileResponseAssembler profileResponseAssembler;
    OutboxEventService outboxEventService;

    @Transactional
    public void followProfile(String followerId, String followeeId) {
        if (followerId.equals(followeeId)) {
            throw new AppException(ErrorCode.CANNOT_FOLLOW_YOURSELF);
        }

        UserProfile follower = findProfile(followerId);
        ensureProfileExists(followeeId);

        outboxEventService.save(
                "FOLLOW",
                followeeId,
                "USER_FOLLOWED",
                Map.of(
                        "followerId", followerId,
                        "followeeId", followeeId,
                        "followerName", fullName(follower),
                        "action", "FOLLOWED"));
        log.info("Follow event registered in Outbox from {} to {}", followerId, followeeId);
    }

    @Transactional
    public void unfollowProfile(String followerId, String followeeId) {
        ensureProfileExists(followerId);
        ensureProfileExists(followeeId);

        outboxEventService.save(
                "FOLLOW",
                followeeId,
                "USER_UNFOLLOWED",
                Map.of(
                        "followerId", followerId,
                        "followeeId", followeeId,
                        "action", "UNFOLLOWED"));
        log.info("Unfollow event registered in Outbox from {} to {}", followerId, followeeId);
    }

    @Transactional(readOnly = true)
    public PageResponse<ProfileResponse> getFollowers(String profileId, int page, int size) {
        ensureProfileExists(profileId);
        int safePage = normalizePage(page);
        int safeSize = normalizeSize(size);
        Pageable pageable = PageRequest.of(safePage - 1, safeSize);
        Page<UserProfileNode> nodes = neo4jRepository.findFollowers(profileId, pageable);
        return toPageResponse(nodes, safePage, safeSize);
    }

    @Transactional(readOnly = true)
    public PageResponse<ProfileResponse> getFollowing(String profileId, int page, int size) {
        ensureProfileExists(profileId);
        int safePage = normalizePage(page);
        int safeSize = normalizeSize(size);
        Pageable pageable = PageRequest.of(safePage - 1, safeSize);
        Page<UserProfileNode> nodes = neo4jRepository.findFollowing(profileId, pageable);
        return toPageResponse(nodes, safePage, safeSize);
    }

    private PageResponse<ProfileResponse> toPageResponse(Page<UserProfileNode> nodes, int page, int size) {
        List<String> ids =
                nodes.getContent().stream().map(UserProfileNode::getId).toList();

        List<ProfileResponse> responses = profileResponseAssembler.toResponsesByIdsPreservingOrder(ids, true);

        return PageResponse.<ProfileResponse>builder()
                .currentPage(page)
                .totalPages(nodes.getTotalPages())
                .pageSize(size)
                .totalElements(nodes.getTotalElements())
                .data(responses)
                .build();
    }

    private UserProfile findProfile(String profileId) {
        return jpaRepository.findById(profileId).orElseThrow(() -> new AppException(ErrorCode.PROFILE_NOT_FOUND));
    }

    private void ensureProfileExists(String profileId) {
        if (!jpaRepository.existsById(profileId)) {
            throw new AppException(ErrorCode.PROFILE_NOT_FOUND);
        }
    }

    private String fullName(UserProfile profile) {
        return ((profile.getLastName() != null ? profile.getLastName() : "") + " "
                + (profile.getFirstName() != null ? profile.getFirstName() : "")).trim();
    }

    private int normalizePage(int page) {
        return Math.max(page, 1);
    }

    private int normalizeSize(int size) {
        return size > 0 ? size : 10;
    }
}
