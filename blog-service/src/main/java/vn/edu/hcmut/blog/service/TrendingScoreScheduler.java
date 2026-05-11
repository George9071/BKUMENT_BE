//package vn.edu.hcmut.blog.service;
//
//import java.time.LocalDateTime;
//import java.time.temporal.ChronoUnit;
//import java.util.List;
//import java.util.Map;
//import java.util.stream.Collectors;
//
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.context.annotation.Lazy;
//import org.springframework.scheduling.annotation.Scheduled;
//import org.springframework.stereotype.Component;
//
//import lombok.AccessLevel;
//import lombok.experimental.FieldDefaults;
//import lombok.experimental.NonFinal;
//import lombok.extern.slf4j.Slf4j;
//import vn.edu.hcmut.blog.dto.response.ResourceEngagementStatsResponse;
//import vn.edu.hcmut.blog.entity.Post;
//import vn.edu.hcmut.blog.repository.PostRepository;
//import vn.edu.hcmut.blog.repository.httpclient.SocialClient;
//
///**
// * Cronjob chạy mỗi 1 giờ để tính lại trendingScore cho các bài viết trong 30 ngày gần nhất.
// * Công thức HackerNews:
// *   Interactions = w1*(avgRate * log10(1+numRates)) + w2*views + w3*comments
// *   TrendingScore = Interactions / (ageInHours + 2)^gravity
// */
//@Slf4j
//@Component
//@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
//public class TrendingScoreScheduler {
//
//    PostRepository postRepository;
//    SocialClient socialClient;
//
//    @NonFinal
//    @Value("${app.trending.w1:100.0}")
//    double w1;
//
//    @NonFinal
//    @Value("${app.trending.w2:1.0}")
//    double w2;
//
//    @NonFinal
//    @Value("${app.trending.w3:5.0}")
//    double w3;
//
//    @NonFinal
//    @Value("${app.trending.gravity:1.8}")
//    double gravity;
//
//    @NonFinal
//    @Value("${app.trending.window-days:30}")
//    int windowDays;
//
//    public TrendingScoreScheduler(PostRepository postRepository, @Lazy SocialClient socialClient) {
//        this.postRepository = postRepository;
//        this.socialClient = socialClient;
//    }
//
//    @Scheduled(fixedRateString = "${app.trending.refresh-interval-ms:3600000}")
//    public void recalculateTrendingScores() {
//        log.info("[TrendingScheduler] Bắt đầu tính lại trending scores...");
//
//        LocalDateTime since = LocalDateTime.now().minusDays(windowDays);
//        List<Post> posts = postRepository.findRecentPostsForScoring(since);
//
//        if (posts.isEmpty()) {
//            log.info("[TrendingScheduler] Không có bài viết nào trong {} ngày gần nhất.", windowDays);
//            return;
//        }
//
//        // Fetch engagement stats một lần từ social-service
//        Map<String, ResourceEngagementStatsResponse> engagementMap;
//        try {
//            List<ResourceEngagementStatsResponse> engagementStats = socialClient.getEngagementStats();
//            engagementMap = (engagementStats != null)
//                    ? engagementStats.stream()
//                            .collect(Collectors.toMap(ResourceEngagementStatsResponse::getResourceId, s -> s))
//                    : Map.of();
//        } catch (Exception e) {
//            log.error("[TrendingScheduler] Không thể lấy engagement stats từ social-service. Bỏ qua lần này.", e);
//            return;
//        }
//
//        LocalDateTime now = LocalDateTime.now();
//        int updated = 0;
//
//        for (Post post : posts) {
//            try {
//                ResourceEngagementStatsResponse stats = engagementMap.get(post.getId());
//                double avgRate = (stats != null && stats.getAverageRating() != null) ? stats.getAverageRating() : 0.0;
//                long numRates = (stats != null && stats.getRatingCount() != null) ? stats.getRatingCount() : 0L;
//                long comments = (stats != null && stats.getCommentCount() != null) ? stats.getCommentCount() : 0L;
//                long views = post.getViews() != null ? post.getViews() : 0L;
//
//                long ageInHours = ChronoUnit.HOURS.between(post.getCreatedAt(), now);
//
//                // Trending_Score = (w1*(avgRate*log10(1+numRates)) + w2*views + w3*comments)
//                //                  / (ageInHours + 2)^gravity
//                double numerator = (w1 * (avgRate * Math.log10(1 + numRates))) + (w2 * views) + (w3 * comments);
//                double denominator = Math.pow(ageInHours + 2, gravity);
//                double score = (denominator > 0) ? numerator / denominator : 0.0;
//
//                postRepository.updateTrendingScore(post.getId(), score);
//                updated++;
//            } catch (Exception e) {
//                log.error("[TrendingScheduler] Lỗi khi tính score cho bài {}: {}", post.getId(), e.getMessage());
//            }
//        }
//
//        log.info("[TrendingScheduler] Hoàn thành. Đã cập nhật {} / {} bài viết.", updated, posts.size());
//    }
//}
