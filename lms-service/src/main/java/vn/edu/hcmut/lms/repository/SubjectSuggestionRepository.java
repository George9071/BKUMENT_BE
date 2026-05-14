package vn.edu.hcmut.lms.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.edu.hcmut.lms.constant.SuggestionStatus;
import vn.edu.hcmut.lms.constant.SuggestionType;
import vn.edu.hcmut.lms.entity.SubjectSuggestion;

@Repository
public interface SubjectSuggestionRepository extends JpaRepository<SubjectSuggestion, String> {

    /* Anti-abuse: same user cannot send two PENDING requests with the same name and type. */
    boolean existsByReporterIdAndTypeAndProposedNameIgnoreCaseAndStatus(
            String reporterId,
            SuggestionType type,
            String proposedName,
            SuggestionStatus status);

    /* Avoid duplicate pending proposals with the same name among users. */
    boolean existsByTypeAndProposedNameIgnoreCaseAndStatus(
            SuggestionType type, String proposedName, SuggestionStatus status);

    Page<SubjectSuggestion> findByReporterId(String reporterId, Pageable pageable);

    Page<SubjectSuggestion> findByReporterIdAndStatus(
            String reporterId, SuggestionStatus status, Pageable pageable);

    /** Admin queue. */
    Page<SubjectSuggestion> findByStatus(SuggestionStatus status, Pageable pageable);

    /** Admin queue with filter */
    Page<SubjectSuggestion> findByStatusAndType(
            SuggestionStatus status, SuggestionType type, Pageable pageable);
}
