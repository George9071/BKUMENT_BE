package vn.edu.hcmut.profile.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import vn.edu.hcmut.profile.dto.request.ProfileCreationRequest;
import vn.edu.hcmut.profile.dto.request.ProfileUpdateRequest;
import vn.edu.hcmut.profile.dto.response.PageResponse;
import vn.edu.hcmut.profile.dto.response.ProfileResponse;
import vn.edu.hcmut.profile.entity.jpa.University;
import vn.edu.hcmut.profile.entity.jpa.UserProfile;
import vn.edu.hcmut.profile.exception.AppException;
import vn.edu.hcmut.profile.exception.ErrorCode;
import vn.edu.hcmut.profile.mapper.ProfileMapper;
import vn.edu.hcmut.profile.repository.UniversityRepository;
import vn.edu.hcmut.profile.repository.UserProfileRepository;
import vn.edu.hcmut.profile.service.assembler.ProfileResponseAssembler;
import vn.edu.hcmut.profile.service.outbox.OutboxEventService;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class ProfileService {

    UserProfileRepository jpaRepository;
    UniversityRepository universityRepository;

    ProfileNeo4jService profileNeo4jService;

    ProfileMapper profileMapper;
    ProfileResponseAssembler profileResponseAssembler;
    OutboxEventService outboxEventService;

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

        University university = universityRepository
                .findById(request.getUniversityId())
                .orElseThrow(() -> new AppException(ErrorCode.UNIVERSITY_NOT_FOUND));

        String profileId = UUID.randomUUID().toString();

        UserProfile user = profileMapper.toProfile(request);
        user.setId(profileId);
        user.setUniversityId(university.getId());
        jpaRepository.save(user);

        outboxEventService.save("PROFILE", profileId, "PROFILE_CREATED", request);

        return profileResponseAssembler.toResponse(user, false);
    }

    /**
     * Updates profile data. If university changes, syncs the update to Neo4j.
     * Publishes a profile-events Kafka event after save.
     */
    @Transactional(transactionManager = "transactionManager", rollbackFor = Exception.class)
    public ProfileResponse updateProfile(ProfileUpdateRequest request) {
        String accountId = getCurrentAccountId();
        UserProfile user = jpaRepository.findByAccountId(accountId)
                .orElseThrow(() -> new AppException(ErrorCode.PROFILE_NOT_FOUND));

        profileMapper.updateProfile(user, request);

        boolean universityChanged = applyUniversityUpdate(request, user);
        jpaRepository.save(user);

        ProfileUpdateRequest syncPayload = buildProfileSyncPayload(request, user, universityChanged);
        outboxEventService.save("PROFILE", user.getId(), "PROFILE_UPDATED", syncPayload);
        outboxEventService.save(
                "PROFILE",
                user.getId(),
                "PROFILE_UPDATED_FOR_COMMUNICATION",
                profileMapper.toProfileUpdatedEvent(user));

        return profileResponseAssembler.toResponse(user, true);
    }

    /**
     * Hard-deletes a profile from both JPA and Neo4j (DETACH DELETE).
     */
    @Transactional(transactionManager = "transactionManager", rollbackFor = Exception.class)
    public void deleteProfile(String profileId) {
        UserProfile user = jpaRepository.findById(profileId)
                .orElseThrow(() -> new AppException(ErrorCode.PROFILE_NOT_FOUND));

        jpaRepository.delete(user);

        outboxEventService.save("PROFILE", profileId, "PROFILE_DELETED", Map.of("profileId", profileId));
        log.info("Registered hard-delete event in Outbox for profileId {}", profileId);
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
        return profileResponseAssembler.toResponse(user, true);
    }

    @Transactional
    public void updateMyInterests(List<String> topicIds) {
        String accountId = getCurrentAccountId();
        UserProfile user = jpaRepository
                .findByAccountId(accountId)
                .orElseThrow(() -> new AppException(ErrorCode.PROFILE_NOT_FOUND));

        profileNeo4jService.updateUserInterests(user.getId(), topicIds);
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

        return profileResponseAssembler.toResponse(user, true);
    }

    @Transactional(readOnly = true)
    public ProfileResponse getProfileByEmail(String email) {
        UserProfile profile =
                jpaRepository.findByEmail(email).orElseThrow(() -> new AppException(ErrorCode.PROFILE_NOT_FOUND));

        return profileResponseAssembler.toResponse(profile, true);
    }

    @Transactional(readOnly = true)
    public ProfileResponse getMyProfile() {
        return getProfileByAccountId(getCurrentAccountId());
    }

    /**
     * Batch fetch — used by other microservices via FeignClient.
     * Does NOT include follower/following counts (too expensive for batch).
     */
    @Transactional(readOnly = true)
    public List<ProfileResponse> getProfilesByIds(List<String> profileIds) {
        return profileResponseAssembler.toResponsesByIdsPreservingOrder(profileIds, false);
    }

    @Transactional(readOnly = true)
    public PageResponse<ProfileResponse> searchProfile(String keyword, int page, int size) {
        int safePage = normalizePage(page);
        int safeSize = normalizeSize(size);

        if (keyword == null || keyword.isBlank()) {
            return profileResponseAssembler.emptyProfilePage(safePage, safeSize);
        }

        var pageable = PageRequest.of(safePage - 1, safeSize);
        var pageData = jpaRepository.search(keyword.trim(), pageable);

        List<UserProfile> users = pageData.getContent();
        List<ProfileResponse> profiles = profileResponseAssembler.toResponses(users, true);

        return PageResponse.<ProfileResponse>builder()
                .currentPage(safePage)
                .pageSize(safeSize)
                .totalPages(pageData.getTotalPages())
                .totalElements(pageData.getTotalElements())
                .data(profiles)
                .build();
    }

    private boolean applyUniversityUpdate(ProfileUpdateRequest request, UserProfile user) {
        Integer universityId = request.getUniversityId();
        if (universityId == null || universityId.equals(user.getUniversityId())) {
            return false;
        }

        University newUniversity = universityRepository.findById(universityId)
                .orElseThrow(() -> new AppException(ErrorCode.UNIVERSITY_NOT_FOUND));
        user.setUniversityId(newUniversity.getId());
        return true;
    }

    private ProfileUpdateRequest buildProfileSyncPayload(
            ProfileUpdateRequest request, UserProfile user, boolean universityChanged) {
        boolean nameChanged = request.getFirstName() != null || request.getLastName() != null;

        return ProfileUpdateRequest.builder()
                .firstName(nameChanged ? user.getFirstName() : null)
                .lastName(nameChanged ? user.getLastName() : null)
                .dob(request.getDob())
                .bio(request.getBio())
                .avatarUrl(request.getAvatarUrl())
                .address(request.getAddress())
                .gender(request.getGender())
                .phone(request.getPhone())
                .universityId(universityChanged ? user.getUniversityId() : null)
                .build();
    }

    private int normalizePage(int page) {
        return Math.max(page, 1);
    }

    private int normalizeSize(int size) {
        return size > 0 ? size : 10;
    }

    private String getCurrentAccountId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        return authentication.getName();
    }
}
