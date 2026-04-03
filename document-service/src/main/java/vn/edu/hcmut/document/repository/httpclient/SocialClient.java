package vn.edu.hcmut.document.repository.httpclient;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

import vn.edu.hcmut.document.dto.response.APIResponse;
import vn.edu.hcmut.document.dto.response.RankingStatsResponse;

@FeignClient(name = "social-service", url = "${app.services.social}")
public interface SocialClient {

    @GetMapping("/ratings/ranking-stats")
    APIResponse<RankingStatsResponse> getRankingStats();
}
