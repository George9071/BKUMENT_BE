package vn.edu.hcmut.blog.repository.httpclient;

import java.util.List;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import vn.edu.hcmut.blog.dto.response.ResourceEngagementStatsResponse;
import vn.edu.hcmut.blog.dto.response.SocialReportResponse;

@FeignClient(name = "social-service", url = "${app.services.social}")
public interface SocialClient {
    @DeleteMapping("/internal/resource/{resourceId}")
    void deleteSocialByResourceId(@PathVariable("resourceId") String resourceId);

    @GetMapping("/ratings/internal/engagement-stats")
    List<ResourceEngagementStatsResponse> getEngagementStats();

    @PostMapping("/internal/reports/by-targets")
    List<SocialReportResponse> getReportsByTargetIds(@RequestBody List<String> targetIds);
}
