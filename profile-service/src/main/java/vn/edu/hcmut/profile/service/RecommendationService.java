package vn.edu.hcmut.profile.service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
import vn.edu.hcmut.profile.service.assembler.ProfileResponseAssembler;

@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RecommendationService {
    Neo4jClient neo4jClient;
    ProfileResponseAssembler profileResponseAssembler;

    static final int SCORE_MUTUAL_FOLLOW = 5;
    static final int SCORE_SAME_CLASS = 3;
    static final int SCORE_SAME_UNIVERSITY = 1;

    /**
     * Returns a paginated list of recommended profiles for the given user.
     */
    @Transactional(readOnly = true)
    public PageResponse<ProfileResponse> getPeopleYouMayKnow(String profileId, int page, int size) {
        int safePage = normalizePage(page);
        int safeSize = normalizeSize(size);
        int skip = (safePage - 1) * safeSize;

        Map<String, Object> params = Map.of(
                "profileId", profileId,
                "mutualScore", SCORE_MUTUAL_FOLLOW,
                "classScore", SCORE_SAME_CLASS,
                "uniScore", SCORE_SAME_UNIVERSITY,
                "skip", skip,
                "limit", safeSize);

        try {
            long totalElements = fetchTotalCount(profileId);
            if (totalElements == 0) return emptyPage(safePage, safeSize);

            List<String> recommendedIds = fetchRecommendedIds(params);
            if (recommendedIds.isEmpty()) return emptyPage(safePage, safeSize);

            List<ProfileResponse> responses =
                    profileResponseAssembler.toResponsesByIdsPreservingOrder(recommendedIds, true);

            int totalPages = (int) Math.ceil((double) totalElements / safeSize);

            return PageResponse.<ProfileResponse>builder()
                    .currentPage(safePage)
                    .totalPages(totalPages)
                    .pageSize(safeSize)
                    .totalElements(totalElements)
                    .data(responses)
                    .build();

        } catch (Exception e) {
            log.error("Failed to fetch recommendations from Neo4j for profile {}: {}", profileId, e.getMessage());
            return emptyPage(safePage, safeSize);
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
                .orElse(Collections.emptyList());
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

    private int normalizePage(int page) {
        return Math.max(page, 1);
    }

    private int normalizeSize(int size) {
        return size > 0 ? size : 10;
    }
}
