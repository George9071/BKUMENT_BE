package vn.edu.hcmut.social.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import vn.edu.hcmut.social.dto.request.ReportRequest;
import vn.edu.hcmut.social.dto.response.ReportResponse;
import vn.edu.hcmut.social.entity.Report;
import vn.edu.hcmut.social.enums.ReportStatus;
import vn.edu.hcmut.social.enums.ReportType;
import vn.edu.hcmut.social.exception.AppException;
import vn.edu.hcmut.social.exception.ErrorCode;
import vn.edu.hcmut.social.repository.ReportRepository;
import vn.edu.hcmut.social.repository.httpclient.BlogClient;
import vn.edu.hcmut.social.repository.httpclient.DocumentClient;
import vn.edu.hcmut.social.repository.httpclient.ProfileClient;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class ReportService {
    ReportRepository reportRepository;
    ProfileClient profileClient;
    DocumentClient documentClient;
    BlogClient blogClient;

    private String getOwnerId(String targetId, ReportType type) {
        try {
            if (ReportType.DOCUMENT.equals(type)) {
                return documentClient.getOwnerId(targetId);
            } else if (ReportType.BLOG.equals(type)) {
                return blogClient.getOwnerId(targetId);
            }
        } catch (Exception e) {
            log.error("Failed to fetch owner for {} of type {}: {}", targetId, type, e.getMessage());
        }
        return null;
    }

    @Transactional
    public ReportResponse createReport(ReportRequest request, String reporterId) {
        Report report = Report.builder()
                .reporterId(reporterId)
                .targetId(request.getTargetId())
                .type(request.getType())
                .reason(request.getReason())
                .detail(request.getDetail())
                .status(ReportStatus.PENDING)
                .isDeleted(false)
                .build();

        report = reportRepository.save(report);

        return toReportResponse(report);
    }

    @Transactional
    public ReportResponse updateReportStatus(String reportId, ReportStatus status, String resolverId) {
        Report report =
                reportRepository.findById(reportId).orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_EXISTED));

        if (report.isDeleted()) {
            throw new AppException(ErrorCode.RESOURCE_NOT_EXISTED);
        }

        report.setStatus(status);
        report.setResolverId(resolverId);

        // Points hook: deduct points if report is APPROVED
        if (ReportStatus.APPROVED.equals(status)) {
            String ownerId = getOwnerId(report.getTargetId(), report.getType());
            if (ownerId != null) {
                try {
                    profileClient.updatePoints(ownerId, -50L);
                    log.info("Deducted 50 points from profile {} due to approved report {}", ownerId, reportId);
                } catch (Exception e) {
                    log.error("Failed to deduct points from profile {}: {}", ownerId, e.getMessage());
                }
            }
        }

        report = reportRepository.save(report);
        return toReportResponse(report);
    }

    @Transactional
    public void deleteReport(String reportId) {
        Report report =
                reportRepository.findById(reportId).orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_EXISTED));

        report.setDeleted(true);
        reportRepository.save(report);
    }

    public Page<ReportResponse> getAllReports(String statusStr, Pageable pageable) {
        if (statusStr == null || statusStr.isBlank()) {
            return reportRepository.findByIsDeletedFalse(pageable).map(this::toReportResponse);
        }

        try {
            ReportStatus status = ReportStatus.valueOf(statusStr.toUpperCase());
            return reportRepository
                    .findByStatusAndIsDeletedFalse(status, pageable)
                    .map(this::toReportResponse);
        } catch (IllegalArgumentException e) {
            throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION);
        }
    }

    private ReportResponse toReportResponse(Report report) {
        return ReportResponse.builder()
                .id(report.getId())
                .resolverId(report.getResolverId())
                .reporterId(report.getReporterId())
                .targetId(report.getTargetId())
                .status(report.getStatus())
                .type(report.getType())
                .reason(report.getReason())
                .detail(report.getDetail())
                .createdAt(report.getCreatedAt())
                .updatedAt(report.getUpdatedAt())
                .build();
    }
}
