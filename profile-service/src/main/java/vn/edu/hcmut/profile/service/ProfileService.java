package vn.edu.hcmut.profile.service;

import java.util.*;

import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import vn.edu.hcmut.event.ProfileUpdatedEvent;
import vn.edu.hcmut.profile.dto.request.ProfileCreationRequest;
import vn.edu.hcmut.profile.dto.request.ProfileUpdateRequest;
import vn.edu.hcmut.profile.dto.response.ProfileResponse;
import vn.edu.hcmut.profile.entity.jpa.University;
import vn.edu.hcmut.profile.entity.jpa.UserProfile;
import vn.edu.hcmut.profile.exception.AppException;
import vn.edu.hcmut.profile.exception.ErrorCode;
import vn.edu.hcmut.profile.mapper.ProfileMapper;
import vn.edu.hcmut.profile.repository.UniversityRepository;
import vn.edu.hcmut.profile.repository.UserProfileRepository;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class ProfileService {

    UserProfileRepository jpaRepository;
    UniversityRepository universityRepository;

    ProfileMapper profileMapper;
    ProfileNeo4jService profileNeo4jService;

    KafkaTemplate<String, ProfileUpdatedEvent> kafkaTemplate;

    /**
     * Creates a profile in both JPA and Neo4j
     * Uses the same UUID to ensure 1-1 mapping between the two databases.
     */
    @Transactional(transactionManager = "transactionManager", rollbackFor = Exception.class)
    public ProfileResponse createProfile(ProfileCreationRequest request) {
        if (jpaRepository.existsByAccountId(request.getAccountId())) {
            throw new AppException(ErrorCode.PROFILE_ALREADY_EXISTS);
        }

        if (jpaRepository.existsByEmail(request.getEmail())) {
            throw new AppException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

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
        profileNeo4jService.createUserNode(profileId, request, university);

        return buildProfileResponse(user, university, 0, 0);
    }

    /**
     * Updates profile data. If university changes, syncs the update to Neo4j.
     * Publishes a profile-events Kafka event after save.
     */
    @Transactional(transactionManager = "transactionManager", rollbackFor = Exception.class)
    public ProfileResponse updateProfile(ProfileUpdateRequest request) {
        String accountId = getCurrentAccountId();

        UserProfile user = jpaRepository
                .findByAccountId(accountId)
                .orElseThrow(() -> new AppException(ErrorCode.PROFILE_NOT_FOUND));

        profileMapper.updateProfile(user, request);

        University university = resolveUniversityUpdate(request, user);

        jpaRepository.save(user);

        kafkaTemplate.send(
                "profile-update-events",
                user.getId(),
                ProfileUpdatedEvent.builder()
                        .profileId(user.getId())
                        .firstName(user.getFirstName())
                        .lastName(user.getLastName())
                        .avatar(user.getAvatarUrl())
                        .build());

        return buildProfileResponse(
                user,
                university,
                profileNeo4jService.countFollowers(user.getId()),
                profileNeo4jService.countFollowing(user.getId()));
    }

    /**
     * Hard-deletes a profile from both JPA and Neo4j (DETACH DELETE).
     */
    @Transactional(transactionManager = "transactionManager", rollbackFor = Exception.class)
    public void deleteProfile(String profileId) {
        UserProfile user =
                jpaRepository.findById(profileId).orElseThrow(() -> new AppException(ErrorCode.PROFILE_NOT_FOUND));

        jpaRepository.delete(user);
        profileNeo4jService.deleteUserNode(profileId);

        log.info("Deleted UserProfile {} from JPA and Neo4j", profileId);
    }

    /**
     * Sets emailVerified = true for the given accountId.
     * Called by Identity Service after email verification token is validated.
     */
    @Transactional
    public void verifyEmail(String accountId) {
        UserProfile user = jpaRepository
                .findByAccountId(accountId)
                .orElseThrow(() -> new AppException(ErrorCode.PROFILE_NOT_FOUND));
        user.setEmailVerified(true);
        jpaRepository.save(user);
        log.info("Email verified for accountId {}", accountId);
    }

    @Transactional(readOnly = true)
    public ProfileResponse getProfile(String id) {
        UserProfile user = jpaRepository.findById(id).orElseThrow(() -> new AppException(ErrorCode.PROFILE_NOT_FOUND));
        return toProfileResponse(user);
    }

    @Transactional(transactionManager = "transactionManager", rollbackFor = Exception.class)
    public void updatePoints(String profileId, long delta) {
        UserProfile user =
                jpaRepository.findById(profileId).orElseThrow(() -> new AppException(ErrorCode.PROFILE_NOT_FOUND));

        if (user.getPoints() == null) user.setPoints(0L);

        user.setPoints(user.getPoints() + delta);
        jpaRepository.save(user);

        log.info("Updated points for profile {}: delta={}, new total={}", profileId, delta, user.getPoints());
    }

    @Transactional(readOnly = true)
    public ProfileResponse getProfileByAccountId(String accountId) {
        UserProfile user = jpaRepository
                .findByAccountId(accountId)
                .orElseThrow(() -> new AppException(ErrorCode.PROFILE_NOT_FOUND));

        return toProfileResponse(user);
    }

    @Transactional(readOnly = true)
    public ProfileResponse getProfileByEmail(String email) {
        UserProfile profile =
                jpaRepository.findByEmail(email).orElseThrow(() -> new AppException(ErrorCode.PROFILE_NOT_FOUND));

        return profileMapper.toProfileResponse(profile);
    }

    @Transactional(readOnly = true)
    public ProfileResponse getMyProfile() {
        return getProfileByAccountId(getCurrentAccountId());
    }

    /**
     * Batch fetch — used by other microservices via FeignClient.
     * Does NOT include follower/following counts (too expensive for batch).
     */
    public List<ProfileResponse> getProfilesByIds(List<String> profileIds) {
        return jpaRepository.findAllById(profileIds).stream()
                .map(profileMapper::toProfileResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ProfileResponse> searchProfile(String keyword, int limit) {
        if (keyword == null || keyword.isBlank()) return List.of();

        return jpaRepository.search(keyword.trim(), PageRequest.of(0, limit)).getContent().stream()
                .map(profileMapper::toProfileResponse)
                .toList();
    }

    // -------------------------------------------------------------------------
    // HELPERS
    // -------------------------------------------------------------------------

    private University resolveUniversityUpdate(ProfileUpdateRequest request, UserProfile user) {
        if (request.getUniversityId() == null || request.getUniversityId().equals(user.getUniversityId())) {

            // No change — fetch existing for response
            return user.getUniversityId() != null
                    ? universityRepository.findById(user.getUniversityId()).orElse(null)
                    : null;
        }

        University newUniversity = universityRepository
                .findById(request.getUniversityId())
                .orElseThrow(() -> new AppException(ErrorCode.UNIVERSITY_NOT_FOUND));

        user.setUniversityId(newUniversity.getId());

        // Sync new university to Neo4j
        profileNeo4jService.updateUserUniversity(user.getId(), newUniversity);

        return newUniversity;
    }

    private ProfileResponse toProfileResponse(UserProfile user) {
        University university = user.getUniversityId() != null
                ? universityRepository.findById(user.getUniversityId()).orElse(null)
                : null;

        return buildProfileResponse(
                user,
                university,
                profileNeo4jService.countFollowers(user.getId()),
                profileNeo4jService.countFollowing(user.getId()));
    }

    private ProfileResponse buildProfileResponse(
            UserProfile user, University university, int followerCount, int followingCount) {
        ProfileResponse response = profileMapper.toProfileResponse(user);
        if (university != null) response.setUniversity(university.getName());
        response.setFollowerCount(followerCount);
        response.setFollowingCount(followingCount);
        return response;
    }

    private String getCurrentAccountId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        return authentication.getName();
    }
}
