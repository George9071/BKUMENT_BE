package vn.edu.hcmut.social.controller;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import vn.edu.hcmut.social.dto.response.ReportResponse;
import vn.edu.hcmut.social.service.ReportService;
import vn.edu.hcmut.social.service.ResourceCleanupService;

@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class InternalSocialController {
    ReportService reportService;
    ResourceCleanupService resourceCleanupService;

    @DeleteMapping("/resource/{resourceId}")
    public void deleteSocialByResourceId(@PathVariable String resourceId) {
        log.info("[INTERNAL-CLEANUP] Starting social data cleanup for resource: {}", resourceId);
        resourceCleanupService.cleanupResource(resourceId);
    }

    @PostMapping("/reports/by-targets")
    public List<ReportResponse> getReportsByTargetIds(@RequestBody List<String> targetIds) {
        return reportService.getReportsByTargetIds(targetIds);
    }
}
