package vn.edu.hcmut.document.service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import vn.edu.hcmut.document.dto.response.ResourceEngagementStatsResponse;
import vn.edu.hcmut.document.entity.Document;
import vn.edu.hcmut.document.repository.DocumentRepository;
import vn.edu.hcmut.document.repository.httpclient.SocialClient;

/**
 * Scheduled job that periodically recomputes the ranking_score column on the for all documents within the configured time window.
 * * * *
 *  ── Scoring formula (time-decay, HackerNews-inspired) ────────────────────────
 *      interactions = weightRating  * avgRating * log10(1 + numRates)
 *                      + weightDownload * log10(1 + downloads)
 *                      + weightView     * log10(1 + views)
 *      ranking_score = interactions / (ageInHours + 2) ^ gravity
 * * * *
 * ── Scaling considerations ────────────────────────────────────────────────────
 * In a multi-instance deployment, all instances will execute this scheduler concurrently,
 * issuing duplicate UPDATE statements for every document.
 * Mitigation options (choose one):
 *   1. ShedLock — add @SchedulerLock so only one instance runs per cycle.
 *   2. Leader election — disable the scheduler on all but the elected leader pod.
 *   3. Dedicated worker instance — run the scheduler on a separate node.
 * * * *
 * ── Configuration properties ──────────────────────────────────────────────────
 *   app.ranking.weight-rating           (default 0.7)   — rating term weight
 *   app.ranking.weight-download         (default 0.3)   — download term weight
 *   app.ranking.weight-view             (default 0.1)   — view term weight
 *   app.ranking.gravity                 (default 1.5)   — time-decay exponent
 *   app.ranking.window-days             (default 90)    — only score docs this recent
 *   app.ranking.refresh-interval-ms     (default 3600000 = 1 hour) — run frequency
 *   app.ranking.chunk-size              (default 500)   — rows loaded per DB page
 */
@Slf4j
@Component
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DocumentRankingScheduler {

    DocumentRepository documentRepository;
    SocialClient socialClient;

    @NonFinal @Value("${app.ranking.weight-rating:0.7}")   double weightRating;
    @NonFinal @Value("${app.ranking.weight-download:0.3}") double weightDownload;
    @NonFinal @Value("${app.ranking.weight-view:0.1}")     double weightView;
    @NonFinal @Value("${app.ranking.gravity:1.5}")         double gravity;
    @NonFinal @Value("${app.ranking.window-days:90}")      int    windowDays;

    /**
     * Number of Document entities loaded per DB page during scoring.
     * Keeps heap usage bounded at O(chunkSize) instead of O(total documents).
     * Default: 500 rows per chunk — tune based on available heap and entity size.
     */
    @NonFinal
    @Value("${app.ranking.chunk-size:500}")
    int chunkSize;

    public DocumentRankingScheduler(DocumentRepository documentRepository, @Lazy SocialClient socialClient) {
        this.documentRepository = documentRepository;
        this.socialClient = socialClient;
    }

    @Scheduled(fixedRateString = "${app.ranking.refresh-interval-ms:3600000}")
    public void recalculateRankingScores() {
        log.info("[RANKING] Starting ranking score refresh (window = {} days)", windowDays);

        LocalDateTime since = LocalDateTime.now().minusDays(windowDays);

        // ── Fetch engagement stats
        Map<String, ResourceEngagementStatsResponse> statMap;
        try {
            List<ResourceEngagementStatsResponse> stats = socialClient.getEngagementStats();
            statMap = (stats != null)
                    ? stats.stream()
                    .collect(Collectors.toMap(
                            ResourceEngagementStatsResponse::getResourceId,
                            s -> s))
                    : Map.of();
        } catch (Exception e) {
            // If the social service is unavailable, skip this cycle entirely
            // rather than computing scores with zero ratings.
            log.error("[RANKING] Cannot fetch engagement stats; skipping this cycle: {}", e.getMessage());
            return;
        }

        // Capture "now" once so age is computed consistently across all chunks.
        LocalDateTime now = LocalDateTime.now();
        int pageIndex = 0;
        int totalUpdated = 0;

        while (true) {
            Page<Document> chunk = documentRepository.findRecentDocumentsByRankingScore(
                    since, PageRequest.of(pageIndex, chunkSize));

            if (chunk.isEmpty()) break;

            for (Document doc : chunk.getContent()) {
                try {
                    double score = computeScore(doc, statMap, now);
                    documentRepository.updateRankingScore(doc.getId(), score);
                    totalUpdated++;
                } catch (Exception e) {
                    log.error("[RANKING] Failed to score document {}: {}", doc.getId(), e.getMessage());
                }
            }

            if (chunk.isLast()) break;
            pageIndex++;
        }

        log.info("[RANKING] Refresh complete — {} documents updated", totalUpdated);
    }

    /**
     * Computes the time-decay ranking score for a single document.
     * * * *
     * Formula:
     *   interactions = weightRating * R * log10(1+N) + weightDownload * log10(1+D) + weightView * log10(1+V)
     *   score        = interactions / (ageInHours + 2) ^ gravity
     * *
     * where:
     *   R = average rating score    (0.0 if no ratings)
     *   N = number of ratings       (log-dampened to prevent single-rating inflation)
     *   D = total download count    (log-dampened)
     *   V = total view count        (log-dampened)
     *   ageInHours = hours since createdAt (floored at 0 for future-dated documents)
     * * * *
     * @param doc           the document to score
     * @param engagementMap pre-fetched map of resourceId → engagement stats
     * @param now           the reference timestamp for age calculation (fixed per scheduler run)
     * @return the computed ranking score; 0.0 if computation fails
     */
    private double computeScore(
            Document doc,
            Map<String, ResourceEngagementStatsResponse> engagementMap,
            LocalDateTime now) {

        ResourceEngagementStatsResponse stats = engagementMap.get(doc.getId());

        double avgRate  = (stats != null && stats.getAverageRating() != null) ? stats.getAverageRating() : 0.0;
        long   numRates = (stats != null && stats.getRatingCount()   != null) ? stats.getRatingCount()   : 0L;
        long   downloads = doc.getDownloadCount() != null ? doc.getDownloadCount() : 0L;
        long   views     = doc.getViews()         != null ? doc.getViews()         : 0L;

        long ageInHours = (doc.getCreatedAt() != null)
                ? Math.max(0, ChronoUnit.HOURS.between(doc.getCreatedAt(), now))
                : 0L;

        double interactions = (weightRating  * avgRate * Math.log10(1 + numRates))
                + (weightDownload * Math.log10(1 + downloads))
                + (weightView     * Math.log10(1 + views));

        double denominator = Math.pow(ageInHours + 2, gravity);
        return (denominator > 0) ? interactions / denominator : 0.0;
    }
}
