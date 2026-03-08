package vn.edu.hcmut.social.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import vn.edu.hcmut.social.dto.request.ReportRequest;
import vn.edu.hcmut.social.dto.response.ReportResponse;
import vn.edu.hcmut.social.entity.Report;
import vn.edu.hcmut.social.enums.ReportStatus;
import vn.edu.hcmut.social.exception.AppException;
import vn.edu.hcmut.social.exception.ErrorCode;
import vn.edu.hcmut.social.repository.ReportRepository;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ReportService {
    ReportRepository reportRepository;

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
