package vn.edu.hcmut.blog.scheduler;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import vn.edu.hcmut.blog.dto.response.ResourceEngagementStatsResponse;
import vn.edu.hcmut.blog.entity.Post;
import vn.edu.hcmut.blog.repository.PostRepository;
import vn.edu.hcmut.blog.repository.httpclient.SocialClient;

/**
 * Periodically recomputes the trending_score for every post within the configured time window.
 * * * *
 * Scoring formula (HackerNews-inspired time decay)
 * ────────────────────────────────────────────────
 *   numerator   = w1 * (avgRating * log10(1 + ratingCount))
 *               + w2 * views
 *               + w3 * commentCount
 *   denominator = (ageInHours + 2) ^ gravity
 *   score       = numerator / denominator
 * *
 *   - log10(1 + N) sub-linear scaling — viral posts cannot drown out quality content.
 *   - +2 in the denominator avoids division-by-near-zero for brand-new posts (age=0).
 *   - gravity controls how fast scores decay with age; higher = faster decay.
 * * * *
 * Configuration
 * ─────────────
 *   app.trending.w1              (default 100.0)        — rating term weight
 *   app.trending.w2              (default 1.0)          — view term weight
 *   app.trending.w3              (default 5.0)          — comment term weight
 *   app.trending.gravity         (default 1.8)          — time-decay exponent
 *   app.trending.window-days     (default 30)           — only score posts this recent
 *   app.trending.refresh-rate-ms (default 3600000 = 1h) — run frequency
 *   app.trending.chunk-size      (default 500)          — DB rows per chunk
 * * * *
 * Multi-instance deployments
 * ──────────────────────────
 * In a horizontally scaled deployment every instance will run this scheduler concurrently and emit duplicate UPDATEs.
 * Mitigation options:
 *   1. ShedLock — only one instance runs each cycle.
 *   2. Leader election — only the elected leader runs the scheduler.
 *   3. Dedicated worker — disable the scheduler on API instances and run it only
 *      on a worker pod via @ConditionalOnProperty.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PostTrendingScheduler {

    PostRepository postRepository;
    SocialClient socialClient;

    @NonFinal @Value("${app.trending.w1:100.0}")     double w1;
    @NonFinal @Value("${app.trending.w2:1.0}")       double w2;
    @NonFinal @Value("${app.trending.w3:5.0}")       double w3;
    @NonFinal @Value("${app.trending.gravity:1.8}")  double gravity;
    @NonFinal @Value("${app.trending.window-days:30}") int   windowDays;
    @NonFinal @Value("${app.trending.chunk-size:500}") int   chunkSize;

    /**
     * Default rate: every 1 hour.
     */
    @Scheduled(fixedRateString = "${app.trending.refresh-rate-ms:3600000}",
            initialDelayString = "${app.trending.initial-delay-ms:60000}")
    public void refreshTrendingScores() {
        log.info("[TRENDING] Starting trending score refresh (window = {} days)", windowDays);

        LocalDateTime since = LocalDateTime.now().minusDays(windowDays);

        Map<String, ResourceEngagementStatsResponse> engagementMap;
        try {
            List<ResourceEngagementStatsResponse> stats = socialClient.getEngagementStats();
            engagementMap = (stats != null)
                    ? stats.stream().collect(Collectors.toMap(
                    ResourceEngagementStatsResponse::getResourceId, s -> s))
                    : Map.of();
        } catch (Exception e) {
            // Skip the whole cycle rather than scoring with zero engagement
            log.error("[TRENDING] Cannot fetch engagement stats; skipping cycle: {}", e.getMessage());
            return;
        }

        // Capture "now" once so age is consistent across all chunks of this run.
        LocalDateTime now = LocalDateTime.now();
        int pageIndex = 0;
        int totalUpdated = 0;

        while (true) {
            Page<Post> chunk = postRepository.findRecentPostsByTrendingScore(
                    since, PageRequest.of(pageIndex, chunkSize));
            if (chunk.isEmpty()) break;

            for (Post post : chunk.getContent()) {
                try {
                    double score = computeScore(post, engagementMap, now);
                    postRepository.updateTrendingScore(post.getId(), score);
                    totalUpdated++;
                } catch (Exception e) {
                    log.error("[TRENDING] Failed to score post {}: {}", post.getId(), e.getMessage());
                }
            }

            if (chunk.isLast()) break;
            pageIndex++;
        }

        log.info("[TRENDING] Refresh complete — {} posts updated", totalUpdated);
    }

    /**
     * Computes the trending score for one post.
     * * * *
     * Defensive defaults:
     *   - Null engagement stats --> all engagement components default to 0.
     *   - Null createdAt        --> treated as age=0 (brand new).
     *   - Denominator <= 0      --> score 0.0 (cannot happen with the +2 offset, but guarded).
     */
    private double computeScore(
            Post post,
            Map<String, ResourceEngagementStatsResponse> engagementMap,
            LocalDateTime now) {

        ResourceEngagementStatsResponse stats = engagementMap.get(post.getId());

        double avgRate  = (stats != null && stats.getAverageRating() != null) ? stats.getAverageRating() : 0.0;
        long   numRates = (stats != null && stats.getRatingCount()   != null) ? stats.getRatingCount()   : 0L;
        long   comments = (stats != null && stats.getCommentCount()  != null) ? stats.getCommentCount()  : 0L;
        long   views    = post.getViews() != null ? post.getViews() : 0L;

        long ageInHours = (post.getCreatedAt() != null)
                ? Math.max(0, ChronoUnit.HOURS.between(post.getCreatedAt(), now))
                : 0L;

        double numerator = (w1 * (avgRate * Math.log10(1 + numRates)))
                + (w2 * views)
                + (w3 * comments);
        double denominator = Math.pow(ageInHours + 2, gravity);
        return (denominator > 0) ? numerator / denominator : 0.0;
    }

}
