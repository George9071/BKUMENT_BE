package vn.edu.hcmut.document.service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
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
 * Cronjob chạy mỗi 1 giờ để tính lại trendingScore cho các tài liệu trong cửa sổ thời gian.
 * Công thức Time-Decay (tương tự HackerNews):
 *   Interactions = weightRating*(avgRate*log10(1+numRates)) + weightDownload*log10(1+downloads) + weightView*log10(1+views)
 *   TrendingScore  = Interactions / (ageInHours + 2)^gravity
 */
@Slf4j
@Component
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DocumentRankingScheduler {

    DocumentRepository documentRepository;
    SocialClient socialClient;

    @NonFinal
    @Value("${app.ranking.weight-rating:0.7}")
    double weightRating;

    @NonFinal
    @Value("${app.ranking.weight-download:0.3}")
    double weightDownload;

    @NonFinal
    @Value("${app.ranking.weight-view:0.1}")
    double weightView;

    @NonFinal
    @Value("${app.ranking.gravity:1.5}")
    double gravity;

    @NonFinal
    @Value("${app.ranking.window-days:90}")
    int windowDays;

    public DocumentRankingScheduler(DocumentRepository documentRepository, @Lazy SocialClient socialClient) {
        this.documentRepository = documentRepository;
        this.socialClient = socialClient;
    }

    @Scheduled(fixedRateString = "${app.ranking.refresh-interval-ms:3600000}")
    public void recalculateTrendingScores() {
        log.info("[DocumentRankingScheduler] Bắt đầu tính lại trending scores...");

        LocalDateTime since = LocalDateTime.now().minusDays(windowDays);
        List<Document> documents = documentRepository.findRecentDocumentsForScoring(since);

        if (documents.isEmpty()) {
            log.info("[DocumentRankingScheduler] Không có tài liệu nào trong {} ngày gần nhất.", windowDays);
            return;
        }

        // Fetch engagement stats một lần từ social-service
        Map<String, ResourceEngagementStatsResponse> engagementMap;
        try {
            List<ResourceEngagementStatsResponse> stats = socialClient.getEngagementStats();
            engagementMap = (stats != null)
                    ? stats.stream().collect(Collectors.toMap(ResourceEngagementStatsResponse::getResourceId, s -> s))
                    : Map.of();
        } catch (Exception e) {
            log.error("[DocumentRankingScheduler] Không thể lấy engagement stats. Bỏ qua lần này.", e);
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        int updated = 0;

        for (Document doc : documents) {
            try {
                ResourceEngagementStatsResponse stats = engagementMap.get(doc.getId());
                double avgRate = (stats != null && stats.getAverageRating() != null) ? stats.getAverageRating() : 0.0;
                long numRates = (stats != null && stats.getRatingCount() != null) ? stats.getRatingCount() : 0L;
                long downloads = doc.getDownloadCount() != null ? doc.getDownloadCount() : 0L;
                long views = doc.getViews() != null ? doc.getViews() : 0L;

                long ageInHours = ChronoUnit.HOURS.between(doc.getCreatedAt(), now);

                // Numerator:
                //   ratingTerm    = weightRating * avgRate * log10(1 + numRates)
                //   downloadTerm  = weightDownload * log10(1 + downloads)
                //   viewTerm      = weightView * log10(1 + views)
                double numerator = (weightRating * avgRate * Math.log10(1 + numRates))
                        + (weightDownload * Math.log10(1 + downloads))
                        + (weightView * Math.log10(1 + views));

                double denominator = Math.pow(ageInHours + 2, gravity);
                double score = (denominator > 0) ? numerator / denominator : 0.0;

                documentRepository.updateTrendingScore(doc.getId(), score);
                updated++;
            } catch (Exception e) {
                log.error(
                        "[DocumentRankingScheduler] Lỗi khi tính score cho tài liệu {}: {}",
                        doc.getId(),
                        e.getMessage());
            }
        }

        log.info("[DocumentRankingScheduler] Hoàn thành. Đã cập nhật {} / {} tài liệu.", updated, documents.size());
    }
}
