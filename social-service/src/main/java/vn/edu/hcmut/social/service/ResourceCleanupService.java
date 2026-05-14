package vn.edu.hcmut.social.service;

import jakarta.transaction.Transactional;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import vn.edu.hcmut.social.dto.response.ResourceCleanupResponse;
import vn.edu.hcmut.social.repository.CommentRepository;
import vn.edu.hcmut.social.repository.RatingRepository;
import vn.edu.hcmut.social.repository.ReportRepository;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ResourceCleanupService {

    RatingRepository ratingRepository;
    CommentRepository commentRepository;
    ReportRepository reportRepository;

    @Transactional
    public ResourceCleanupResponse cleanupResource(String resourceId) {
        int deletedRatings  = ratingRepository.deleteByResourceId(resourceId);
        int deletedComments = commentRepository.deleteByResourceId(resourceId);
        int deletedReports  = reportRepository.softDeleteByTargetId(resourceId);

        log.info("[CLEANUP] Resource {}: removed {} ratings, {} comments; soft-deleted {} reports",
                resourceId, deletedRatings, deletedComments, deletedReports);

        return ResourceCleanupResponse.builder()
                .resourceId(resourceId)
                .deletedRatings(deletedRatings)
                .deletedComments(deletedComments)
                .deletedReports(deletedReports)
                .build();
    }
}
