package vn.edu.hcmut.social.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import vn.edu.hcmut.social.dto.request.Recipient;
import vn.edu.hcmut.social.dto.request.ReportRequest;
import vn.edu.hcmut.social.dto.request.SendEmailRequest;
import vn.edu.hcmut.social.dto.response.ProfileResponse;
import vn.edu.hcmut.social.dto.response.ReportResponse;
import vn.edu.hcmut.social.entity.Report;
import vn.edu.hcmut.social.enums.ReportStatus;
import vn.edu.hcmut.social.enums.ReportType;
import vn.edu.hcmut.social.exception.AppException;
import vn.edu.hcmut.social.exception.ErrorCode;
import vn.edu.hcmut.social.repository.CommentRepository;
import vn.edu.hcmut.social.repository.RatingRepository;
import vn.edu.hcmut.social.repository.ReportRepository;
import vn.edu.hcmut.social.repository.httpclient.BlogClient;
import vn.edu.hcmut.social.repository.httpclient.DocumentClient;
import vn.edu.hcmut.social.repository.httpclient.EmailClient;
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
    EmailClient emailClient;
    RatingRepository ratingRepository;
    CommentRepository commentRepository;

    private boolean checkResourceExists(String targetId, ReportType type) {
        try {
            if (ReportType.DOCUMENT.equals(type)) {
                return documentClient.exists(targetId);
            } else if (ReportType.BLOG.equals(type)) {
                return blogClient.exists(targetId);
            }
        } catch (Exception e) {
            log.error("Failed to check existence for {} of type {}: {}", targetId, type, e.getMessage());
        }
        return false;
    }

    private void deleteResource(String targetId, ReportType type) {
        try {
            if (ReportType.DOCUMENT.equals(type)) {
                documentClient.delete(targetId);
            } else if (ReportType.BLOG.equals(type)) {
                blogClient.delete(targetId);
            }
        } catch (Exception e) {
            log.error("Failed to delete resource {} of type {}: {}", targetId, type, e.getMessage());
        }
    }

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

        if (!ReportStatus.PENDING.equals(report.getStatus())) {
            throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION); // Already processed
        }

        // Check if resource exists
        if (!checkResourceExists(report.getTargetId(), report.getType())) {
            throw new AppException(ErrorCode.RESOURCE_NOT_EXISTED);
        }

        report.setStatus(status);
        report.setResolverId(resolverId);

        String ownerId = getOwnerId(report.getTargetId(), report.getType());

        if (ReportStatus.APPROVED.equals(status)) {
            // 1. Deduct points
            if (ownerId != null) {
                try {
                    profileClient.updatePoints(ownerId, -50L);
                    log.info("Deducted 50 points from profile {} due to approved report {}", ownerId, reportId);
                } catch (Exception e) {
                    log.error("Failed to deduct points from profile {}: {}", ownerId, e.getMessage());
                }
            }

            // 2. Delete resource
            deleteResource(report.getTargetId(), report.getType());

            // 3. Cleanup social data (Rating & Comment)
            try {
                ratingRepository.deleteByResourceId(report.getTargetId());
                commentRepository.deleteByResourceId(report.getTargetId());
                log.info("Cleaned up social data for resource {}", report.getTargetId());
            } catch (Exception e) {
                log.error("Failed to cleanup social data for resource {}: {}", report.getTargetId(), e.getMessage());
            }

            // 4. Notify owner
            sendNotificationEmail(ownerId, report, true);
        } else if (ReportStatus.REJECTED.equals(status)) {
            // Notify owner that report was rejected (resource stays)
            sendNotificationEmail(ownerId, report, false);
        }

        // Item 2: USER/TUTOR discipline
        if (ReportStatus.APPROVED.equals(status)
                && (ReportType.USER.equals(report.getType()) || ReportType.TUTOR.equals(report.getType()))) {
            try {
                profileClient.updatePoints(report.getTargetId(), -100L);
                log.info(
                        "Deducted 100 points from profile {} due to approved {} report",
                        report.getTargetId(),
                        report.getType());
            } catch (Exception e) {
                log.error("Failed to deduct points from profile {}: {}", report.getTargetId(), e.getMessage());
            }
        }

        report = reportRepository.save(report);
        return toReportResponse(report);
    }

    private void sendNotificationEmail(String ownerId, Report report, boolean approved) {
        if (ownerId == null) return;

        try {
            ProfileResponse profile = profileClient.findUserProfileById(ownerId);
            if (profile != null && profile.getEmail() != null) {
                String subject = approved ? "Thông báo xử lý báo cáo nội dung" : "Kết quả xử lý báo cáo nội dung";
                String content = approved
                        ? String.format(
                                "Chào %s,<br><br>Nội dung của bạn (ID: %s) đã bị xóa do vi phạm chính sách của chúng tôi (Lý do: %s).<br>Bạn cũng bị trừ 50 điểm tín nhiệm.",
                                profile.getFullName(), report.getTargetId(), report.getReason())
                        : String.format(
                                "Chào %s,<br><br>Báo cáo về nội dung của bạn (ID: %s) đã được xem xét và bác bỏ. Nội dung của bạn vẫn được giữ nguyên.",
                                profile.getFullName(), report.getTargetId());

                SendEmailRequest emailRequest = SendEmailRequest.builder()
                        .to(Recipient.builder()
                                .name(profile.getFullName())
                                .email(profile.getEmail())
                                .build())
                        .subject(subject)
                        .htmlContent(content)
                        .build();

                emailClient.sendEmail(emailRequest);
                log.info("Sent notification email to owner {}", ownerId);
            }
        } catch (Exception e) {
            log.error("Failed to send notification email to profile {}: {}", ownerId, e.getMessage());
        }
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
