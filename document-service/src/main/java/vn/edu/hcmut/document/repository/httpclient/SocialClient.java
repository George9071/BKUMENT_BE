package vn.edu.hcmut.document.repository.httpclient;

import java.util.List;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

import vn.edu.hcmut.document.dto.response.RankingStatsResponse;
import vn.edu.hcmut.document.dto.response.ResourceEngagementStatsResponse;

@FeignClient(name = "social-service", url = "${app.services.social}")
public interface SocialClient {

    @GetMapping("/ratings/internal/ranking-stats")
    RankingStatsResponse getRankingStats();

    @GetMapping("/ratings/internal/engagement-stats")
    List<ResourceEngagementStatsResponse> getEngagementStats();
}
