package vn.edu.hcmut.blog.repository.httpclient;

import java.util.List;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

import vn.edu.hcmut.blog.dto.response.ResourceEngagementStatsResponse;

@FeignClient(name = "social-service", url = "${app.services.social}")
public interface SocialClient {

    @GetMapping("/ratings/internal/engagement-stats")
    List<ResourceEngagementStatsResponse> getEngagementStats();
}
