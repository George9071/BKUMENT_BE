package vn.edu.hcmut.social.service;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import vn.edu.hcmut.social.dto.request.Recipient;
import vn.edu.hcmut.social.dto.request.ReportRequest;
import vn.edu.hcmut.social.dto.request.SendEmailRequest;
import vn.edu.hcmut.social.dto.response.*;
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

    static final int MAX_PAGE_SIZE = 50;

    ReportRepository reportRepository;
    RatingRepository ratingRepository;
    CommentRepository commentRepository;

    ResourceCleanupService resourceCleanupService;

    ProfileClient profileClient;
    DocumentClient documentClient;
    BlogClient blogClient;
    EmailClient emailClient;
    private static final long POINTS_PENALTY = -50L;

    @Transactional(readOnly = true)
    public Page<ContentResponse> getReportedBlogs(String status, Pageable pageable) {
        return getReportedContents(ReportType.BLOG, parseStatusOrNull(status), pageable);
    }

    @Transactional(readOnly = true)
    public Page<ContentResponse> getReportedDocuments(String status, Pageable pageable) {
        return getReportedContents(ReportType.DOCUMENT, parseStatusOrNull(status), pageable);
    }

    private Page<ContentResponse> getReportedContents(ReportType type, ReportStatus status, Pageable pageable) {

        // 1. Group-by-target: which contents are on this page?
        Page<Object[]> grouped = reportRepository.findGroupedTargets(type, status, pageable);
        if (grouped.isEmpty()) return new PageImpl<>(List.of(), pageable, 0L);

        // Preserve the page ordering by using a LinkedHashMap keyed on targetId.
        List<String> targetIds = new ArrayList<>(grouped.getNumberOfElements());
        Map<String, Integer> reportCountByTarget = new LinkedHashMap<>();
        for (Object[] row : grouped.getContent()) {
            String targetId = (String) row[0];
            Long count = (Long) row[1]; // COUNT(*) -> Long
            targetIds.add(targetId);
            reportCountByTarget.put(targetId, count.intValue());
        }

        // 2. Fetch resource metadata in one HTTP batch.
        Map<String, ResourceContentSnapshot> snapshotMap = fetchSnapshotBatch(type, targetIds);

        // 3. Fetch the full report list for these targets (filtered by status).
        List<Report> reports = reportRepository.findReportsForTargets(type, status, targetIds);

        // Group reports by target. Already ordered (targetId, createdAt DESC) by the query.
        Map<String, List<Report>> reportsByTarget = reports.stream()
                .collect(Collectors.groupingBy(Report::getTargetId, LinkedHashMap::new, Collectors.toList()));

        // 4. Collect every profile ID we'll need (authors AND reporters) and batch-fetch once.
        List<String> profileIds = new ArrayList<>();
        snapshotMap.values().forEach(s -> {
            if (s.getOwnerId() != null) profileIds.add(s.getOwnerId());
        });
        reports.forEach(r -> {
            if (r.getReporterId() != null) profileIds.add(r.getReporterId());
        });
        Map<String, ProfileResponse> profileMap =
                fetchProfileMap(profileIds.stream().distinct().toList());

        // 5. Stitch.
        List<ContentResponse> rows = new ArrayList<>(targetIds.size());
        for (String targetId : targetIds) {
            ResourceContentSnapshot snap = snapshotMap.get(targetId);
            // The content may have been deleted between the report being filed and
            // this listing being rendered. Skip it — the moderator will see N-1 rows
            // for that page rather than a broken row. The next page query will
            // backfill from later content.
            if (snap == null) {
                log.warn("Reported {} {} not found in owning service; skipping in listing", type, targetId);
                continue;
            }

            AuthorResponse author = buildAuthor(profileMap.get(snap.getOwnerId()));

            List<ContentResponse.ReportSummary> summaries =
                    reportsByTarget.getOrDefault(targetId, Collections.emptyList()).stream()
                            .map(r -> ContentResponse.ReportSummary.builder()
                                    .reporter(displayName(profileMap.get(r.getReporterId())))
                                    .reason(r.getReason().toString())
                                    .createdAt(r.getCreatedAt())
                                    .build())
                            .toList();

            rows.add(ContentResponse.builder()
                    .id(targetId)
                    .name(snap.getTitle())
                    .author(author)
                    .coverImage(snap.getCoverImage())
                    .content(snap.getContent())
                    .createdAt(snap.getCreatedAt())
                    .views(snap.getViews())
                    .reportCount(reportCountByTarget.get(targetId))
                    .reportList(summaries)
                    .build());
        }

        return new PageImpl<>(rows, pageable, grouped.getTotalElements());
    }

    @Transactional(readOnly = true)
    public List<ReportResponse> getReportsByTargetIds(List<String> targetIds) {
        if (targetIds == null || targetIds.isEmpty()) return Collections.emptyList();

        List<String> deduped = targetIds.stream().distinct().toList();

        Sort sort = Sort.by(Sort.Order.asc("targetId"), Sort.Order.desc("createdAt"));
        List<Report> reports = reportRepository.findByTargetIdInAndDeletedFalse(deduped, sort);

        return reports.stream().map(this::toReportResponse).toList();
    }

    /**
     * Submits a new report against a resource.
     * * * *
     * Anti-abuse guards:
     *   1. Self-report block — a user cannot report their own content.
     *   2. Duplicate report block — a user cannot file multiple PENDING reports for the same resource.
     *      Once their previous report is processed (APPROVED/REJECTED),
     *      they may submit a new one if the issue persists.
     *
     * @param request    payload (targetId, type, reason, optional detail)
     * @param reporterId the authenticated user's profile ID (from JWT)
     */
    @Transactional
    public ReportResponse createReport(ReportRequest request, String reporterId) {
        if (!checkResourceExists(request.getTargetId(), request.getType())) {
            throw new AppException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        String ownerId = getOwnerId(request.getTargetId(), request.getType());
        if (reporterId.equals(ownerId)) throw new AppException(ErrorCode.CANNOT_REPORT_OWN_RESOURCE);

        boolean alreadyPending = reportRepository.existsByReporterIdAndTargetIdAndStatusAndDeletedFalse(
                reporterId, request.getTargetId(), ReportStatus.PENDING);

        if (alreadyPending) throw new AppException(ErrorCode.REPORT_ALREADY_SUBMITTED);

        Report report = Report.builder()
                .reporterId(reporterId)
                .targetId(request.getTargetId())
                .type(request.getType())
                .reason(request.getReason())
                .detail(request.getDetail())
                .status(ReportStatus.PENDING)
                .deleted(false)
                .build();

        report = reportRepository.save(report);
        return toReportResponse(report);
    }

    /**
     * Processes a pending report (admin-only operation).
     * * * *
     *  APPROVED flow:
     *   1. Validate state — status is PENDING, target still exists, not soft-deleted.
     *   2. Save the new report status FIRST (durable state change).
     *   3. Deduct -50 points from the offender's profile.
     *   4. For DOCUMENT / BLOG only: clean up social data + delete the resource.
     *   5. Send a notification email tailored to the report type.
     * * * *
     * REJECTED flow:
     *  1. Same state validation.
     *  2. Save the new status. No email, no penalty, no resource action.
     */
    @Transactional
    public ReportResponse updateReportStatus(String reportId, ReportStatus status, String resolverId) {
        if (ReportStatus.PENDING.equals(status)) {
            throw new AppException(ErrorCode.INVALID_REPORT_STATUS);
        }

        Report report =
                reportRepository.findById(reportId).orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));

        if (report.isDeleted()) throw new AppException(ErrorCode.RESOURCE_NOT_FOUND);

        if (!ReportStatus.PENDING.equals(report.getStatus()))
            throw new AppException(ErrorCode.REPORT_ALREADY_PROCESSED);

        if (!checkResourceExists(report.getTargetId(), report.getType()))
            throw new AppException(ErrorCode.RESOURCE_NOT_FOUND);

        report.setStatus(status);
        report.setResolverId(resolverId);
        report = reportRepository.save(report);

        if (ReportStatus.APPROVED.equals(status)) {
            String ownerId = getOwnerId(report.getTargetId(), report.getType());

            if (ownerId != null) {
                try {
                    profileClient.updatePoints(ownerId, POINTS_PENALTY);
                    log.info(
                            "Applied {} points penalty to profile {} for approved report {}",
                            POINTS_PENALTY,
                            ownerId,
                            reportId);
                } catch (Exception e) {
                    log.error("Failed to apply points penalty to {}: {}", ownerId, e.getMessage());
                }
            }

            // For DOCUMENT / BLOG only: clean up social data and delete the resource.
            // For USER / TUTOR: no-op.
            executeContentRemoval(report.getTargetId(), report.getType());

            sendNotificationEmail(ownerId, report);
        }

        // REJECTED: status saved, nothing else.

        return toReportResponse(report);
    }

    private void sendNotificationEmail(String ownerId, Report report) {
        if (ownerId == null) return;

        try {
            ProfileResponse profile = profileClient.findUserProfileById(ownerId);

            if (profile == null || profile.getEmail() == null) {
                log.warn("Owner {} has no email; skipping notification for report {}", ownerId, report.getId());
                return;
            }

            boolean accountReport = isAccountReport(report.getType());

            String subject = accountReport ? "Thông báo xử lý vi phạm tài khoản" : "Thông báo xử lý báo cáo nội dung";

            String content = accountReport
                    ? String.format(
                            "Chào %s,<br><br>Một báo cáo về tài khoản của bạn đã được xác nhận"
                                    + " vi phạm chính sách (Lý do: %s).<br>Bạn bị trừ 50 điểm hệ thống."
                                    + "<br>Vui lòng xem xét lại hành vi để tránh bị xử lý nặng hơn"
                                    + " trong các lần vi phạm tiếp theo.",
                            profile.getFullName(), report.getReason())
                    : String.format(
                            "Chào %s,<br><br>Nội dung của bạn (ID: %s) đã bị xóa do vi phạm chính sách"
                                    + " của chúng tôi (Lý do: %s).<br>Bạn bị trừ 50 điểm hệ thống.",
                            profile.getFullName(), report.getTargetId(), report.getReason());

            emailClient.sendEmail(SendEmailRequest.builder()
                    .to(Recipient.builder()
                            .name(profile.getFullName())
                            .email(profile.getEmail())
                            .build())
                    .subject(subject)
                    .htmlContent(content)
                    .build());

            log.info("Sent report notification email to owner {}", ownerId);
        } catch (Exception e) {
            log.error("Failed to send notification email to {}: {}", ownerId, e.getMessage());
        }
    }

    @Transactional
    public void deleteReport(String reportId) {
        Report report =
                reportRepository.findById(reportId).orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));

        if (!ReportStatus.PENDING.equals(report.getStatus())) {
            throw new AppException(ErrorCode.CANNOT_DEL_PROCESSED_REPORT);
        }

        report.setDeleted(true);
        reportRepository.save(report);
    }

    private boolean isAccountReport(ReportType type) {
        return type == ReportType.USER || type == ReportType.TUTOR;
    }

    private boolean checkResourceExists(String targetId, ReportType type) {
        try {
            if (type == ReportType.DOCUMENT) return documentClient.exists(targetId);
            if (type == ReportType.BLOG) return blogClient.exists(targetId);
            if (isAccountReport(type)) return profileClient.findUserProfileById(targetId) != null;
            return false;
        } catch (Exception e) {
            log.error("Failed to check existence for {} of type {}: {}", targetId, type, e.getMessage());
            return false;
        }
    }

    /**
     * Resolves the "owner" of the report target — the profile that  receive the points penalty and notification email.
     *   DOCUMENT / BLOG -> the user who uploaded the content.
     *   account types   -> the targetId IS already the profile ID
     *                     (USER and TUTOR share a single profile).
     */
    private String getOwnerId(String targetId, ReportType type) {
        try {
            if (type == ReportType.DOCUMENT) return documentClient.getOwnerId(targetId);
            if (type == ReportType.BLOG) return blogClient.getOwnerId(targetId);
            if (isAccountReport(type)) return targetId;
            return null;
        } catch (Exception e) {
            log.error("Failed to fetch owner for {} of type {}: {}", targetId, type, e.getMessage());
            return null;
        }
    }

    private void executeContentRemoval(String targetId, ReportType type) {
        if (isAccountReport(type)) {
            log.debug("Account-level report {} approved; no resource removal performed", type);
            return;
        }

        resourceCleanupService.cleanupResource(targetId);

        try {
            if (type == ReportType.DOCUMENT) {
                documentClient.delete(targetId);
            } else if (type == ReportType.BLOG) {
                blogClient.delete(targetId);
            }
            log.info("Removed content resource {} of type {}", targetId, type);
        } catch (Exception e) {
            log.error("Failed to remove content resource {} of type {}: {}", targetId, type, e.getMessage());
        }
    }

    private Map<String, ResourceContentSnapshot> fetchSnapshotBatch(ReportType type, List<String> ids) {
        try {
            APIResponse<Map<String, ResourceContentSnapshot>> resp = (type == ReportType.BLOG)
                    ? blogClient.getBlogMetadataBatch(ids)
                    : documentClient.getDocumentMetadataBatch(ids);
            return (resp != null && resp.getResult() != null) ? resp.getResult() : Collections.emptyMap();
        } catch (Exception e) {
            // Downstream outage: return empty so the page renders with rows skipped
            // rather than 500-ing the whole moderation dashboard.
            log.error("Failed to fetch {} metadata batch (size={}): {}", type, ids.size(), e.getMessage());
            return Collections.emptyMap();
        }
    }

    private Map<String, ProfileResponse> fetchProfileMap(List<String> profileIds) {
        if (profileIds.isEmpty()) return Collections.emptyMap();
        try {
            return profileClient.getProfiles(profileIds).stream()
                    .collect(Collectors.toMap(ProfileResponse::getId, Function.identity()));
        } catch (Exception e) {
            log.error("Failed to batch-fetch {} profiles: {}", profileIds.size(), e.getMessage());
            return Collections.emptyMap();
        }
    }

    private AuthorResponse buildAuthor(ProfileResponse profile) {
        if (profile == null) return null;
        return AuthorResponse.builder()
                .id(profile.getId())
                .name(profile.getFullName())
                .avatarUrl(profile.getAvatarUrl())
                .build();
    }

    private String displayName(ProfileResponse profile) {
        return (profile != null) ? profile.getFullName() : "Unknown";
    }

    private ReportStatus parseStatusOrNull(String statusStr) {
        if (statusStr == null || statusStr.isBlank()) return null;
        try {
            return ReportStatus.valueOf(statusStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new AppException(ErrorCode.INVALID_REPORT_STATUS);
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
