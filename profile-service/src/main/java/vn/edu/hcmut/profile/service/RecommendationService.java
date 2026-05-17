package vn.edu.hcmut.profile.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import vn.edu.hcmut.profile.constant.CypherQueries;
import vn.edu.hcmut.profile.dto.response.PageResponse;
import vn.edu.hcmut.profile.dto.response.ProfileResponse;
import vn.edu.hcmut.profile.entity.jpa.University;
import vn.edu.hcmut.profile.entity.jpa.UserProfile;
import vn.edu.hcmut.profile.mapper.ProfileMapper;
import vn.edu.hcmut.profile.repository.UniversityRepository;
import vn.edu.hcmut.profile.repository.UserProfileRepository;

@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RecommendationService {
    Neo4jClient neo4jClient;
    UserProfileRepository jpaRepository;
    UniversityRepository universityRepository;
    ProfileMapper profileMapper;
    ProfileNeo4jService profileNeo4jService;

    static final int SCORE_MUTUAL_FOLLOW = 5;
    static final int SCORE_SAME_CLASS = 3;
    static final int SCORE_SAME_UNIVERSITY = 1;

    /**
     * Returns a paginated list of recommended profiles for the given user.
     */
    @Transactional(readOnly = true)
    public PageResponse<ProfileResponse> getPeopleYouMayKnow(String profileId, int page, int size) {
        int skip = Math.max(0, page - 1) * size;
        int limit = Math.max(1, size);

        Map<String, Object> params = Map.of(
                "profileId", profileId,
                "mutualScore", SCORE_MUTUAL_FOLLOW,
                "classScore", SCORE_SAME_CLASS,
                "uniScore", SCORE_SAME_UNIVERSITY,
                "skip", skip,
                "limit", limit);

        try {
            long totalElements = fetchTotalCount(profileId);
            if (totalElements == 0) return emptyPage(page, size);

            List<String> recommendedIds = fetchRecommendedIds(params);
            if (recommendedIds.isEmpty()) return emptyPage(page, size);

            List<ProfileResponse> responses = fetchProfilesPreservingOrder(recommendedIds);

            Map<String, Map<String, Integer>> countsMap = profileNeo4jService.getBatchCounts(recommendedIds);
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

            int totalPages = (int) Math.ceil((double) totalElements / size);

            return PageResponse.<ProfileResponse>builder()
                    .currentPage(page)
                    .totalPages(totalPages)
                    .pageSize(size)
                    .totalElements(totalElements)
                    .data(responses)
                    .build();

        } catch (Exception e) {
            log.error("Failed to fetch recommendations from Neo4j for profile {}: {}", profileId, e.getMessage());
            return emptyPage(page, size);
        }
    }

    private long fetchTotalCount(String profileId) {
        Optional<Map<String, Object>> result = neo4jClient
                .query(CypherQueries.RECOMMEND_COUNT)
                .bindAll(Map.of("profileId", profileId))
                .fetch()
                .one();

        return result.map(r -> r.get("totalElements"))
                .map(v -> ((Number) v).longValue())
                .orElse(0L);
    }

    @SuppressWarnings("unchecked")
    private List<String> fetchRecommendedIds(Map<String, Object> params) {
        Optional<Map<String, Object>> result = neo4jClient
                .query(CypherQueries.RECOMMEND_IDS)
                .bindAll(params)
                .fetch()
                .one();

        return result.map(r -> r.get("recommendedIds"))
                .map(ids -> (List<String>) ids)
                .orElse(new ArrayList<>());
    }

    /**
     * Fetches JPA details in a single batch, then re-orders by Neo4j score order.
     */
    private List<ProfileResponse> fetchProfilesPreservingOrder(List<String> orderedIds) {
        Map<String, UserProfile> profileMap =
                jpaRepository.findAllById(orderedIds).stream().collect(Collectors.toMap(UserProfile::getId, p -> p));

        List<UserProfile> users = orderedIds.stream()
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

        return responses;
    }

    private PageResponse<ProfileResponse> emptyPage(int page, int size) {
        return PageResponse.<ProfileResponse>builder()
                .currentPage(page)
                .totalPages(0)
                .pageSize(size)
                .totalElements(0)
                .data(Collections.emptyList())
                .build();
    }
}
