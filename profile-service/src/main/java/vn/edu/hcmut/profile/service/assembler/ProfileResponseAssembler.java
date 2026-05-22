package vn.edu.hcmut.profile.service.assembler;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import vn.edu.hcmut.profile.dto.response.PageResponse;
import vn.edu.hcmut.profile.dto.response.ProfileResponse;
import vn.edu.hcmut.profile.entity.jpa.University;
import vn.edu.hcmut.profile.entity.jpa.UserProfile;
import vn.edu.hcmut.profile.entity.records.FollowCounts;
import vn.edu.hcmut.profile.mapper.ProfileMapper;
import vn.edu.hcmut.profile.repository.UniversityRepository;
import vn.edu.hcmut.profile.repository.UserProfileRepository;
import vn.edu.hcmut.profile.service.ProfileNeo4jService;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class ProfileResponseAssembler {
    UserProfileRepository userProfileRepository;
    UniversityRepository universityRepository;
    ProfileMapper profileMapper;
    ProfileNeo4jService profileNeo4jService;

    public ProfileResponse toResponse(UserProfile user, boolean includeFollowCounts) {
        List<ProfileResponse> responses = toResponses(List.of(user), includeFollowCounts);
        return responses.get(0);
    }

    public List<ProfileResponse> toResponses(List<UserProfile> users, boolean includeFollowCounts) {
        if (users == null || users.isEmpty()) return Collections.emptyList();

        List<ProfileResponse> responses = users.stream()
                .map(profileMapper::toProfileResponse)
                .toList();

        hydrateUniversities(users, responses);

        if (includeFollowCounts) hydrateFollowCounts(responses);
        else responses.forEach(this::setEmptyFollowCounts);

        return responses;
    }

    /* Convert a profile IDs list into a profile response list
    *  But still maintaining the original order of the ID list. */
    public List<ProfileResponse> toResponsesByIdsPreservingOrder(
            List<String> orderedProfileIds, boolean includeFollowCounts) {

        if (orderedProfileIds == null || orderedProfileIds.isEmpty()) return Collections.emptyList();

        List<String> ids = orderedProfileIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        Map<String, UserProfile> profileMap = userProfileRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(
                        UserProfile::getId,
                        profile -> profile,
                        (first, second) -> first
                ));

        List<UserProfile> users = ids.stream()
                .map(profileMap::get)
                .filter(Objects::nonNull)
                .toList();

        return toResponses(users, includeFollowCounts);
    }

    public PageResponse<ProfileResponse> emptyProfilePage(int page, int size) {
        return PageResponse.<ProfileResponse>builder()
                .currentPage(page)
                .totalPages(0)
                .pageSize(size)
                .totalElements(0)
                .data(Collections.emptyList())
                .build();
    }

    private void hydrateUniversities(List<UserProfile> users, List<ProfileResponse> responses) {
        List<Integer> universityIds = users.stream()
                .map(UserProfile::getUniversityId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        if (universityIds.isEmpty()) return;

        Map<Integer, String> universityNames = universityRepository.findAllById(universityIds).stream()
                .collect(Collectors.toMap(
                        University::getId,
                        University::getName,
                        (first, second) -> first)
                );

        /* Assume that users[i] corresponds to responses[i] */
        for (int i = 0; i < users.size(); i++) {
            Integer universityId = users.get(i).getUniversityId();
            if (universityId != null) {
                responses.get(i).setUniversity(universityNames.get(universityId));
            }
        }
    }

    private void hydrateFollowCounts(List<ProfileResponse> responses) {
        if (responses == null || responses.isEmpty()) return;

        List<String> profileIds = responses.stream()
                .map(ProfileResponse::getId)
                .filter(Objects::nonNull)
                .toList();

        if (profileIds.isEmpty()) {
            responses.forEach(this::setEmptyFollowCounts);
            return;
        }

        try {
            /*
            profile_id -> FollowCounts(
                followerCount,
                followingCount
            )
             */
            Map<String, FollowCounts> countsByProfile =
                    profileNeo4jService.getBatchCounts(profileIds);

            responses.forEach(response ->
                    applyFollowCounts(response, countsByProfile.get(response.getId()))
            );
        } catch (Exception e) {
            log.error("Failed to fetch follow counts from Neo4j. Falling back to 0 counts.", e);
            responses.forEach(this::setEmptyFollowCounts);
        }
    }

    private void applyFollowCounts(ProfileResponse response, FollowCounts counts) {
        if (counts == null) {
            setEmptyFollowCounts(response);
            return;
        }

        response.setFollowerCount(counts.followerCount());
        response.setFollowingCount(counts.followingCount());
    }

    private void setEmptyFollowCounts(ProfileResponse response) {
        response.setFollowerCount(0);
        response.setFollowingCount(0);
    }
}
