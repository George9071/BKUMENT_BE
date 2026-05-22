package vn.edu.hcmut.lms.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import vn.edu.hcmut.lms.constant.EnrollmentStatus;
import vn.edu.hcmut.lms.dto.sync.*;
import vn.edu.hcmut.lms.entity.Tutor;
import vn.edu.hcmut.lms.repository.*;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SyncService {
    private final SubjectRepository subjectRepository;
    private final TopicRepository topicRepository;
    private final TutorRepository tutorRepository;
    private final ClassRoomRepository classRoomRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final GraphSyncService graphSyncService;

    /**
     * Syncs all Subjects and Topics (with BELONGS_TO relationships) to Neo4j.
     * Order matters — Topics must be synced after Subjects to resolve the relationship.
     */
    public void syncAllMetadata() {
        syncSubjects();
        syncTopics();
    }

    /**
     * Syncs all Tutor → Subject (TEACHES) relationships to Neo4j.
     * Skips tutors with no subjects assigned.
     */
    public void syncAllTutorSubjects() {
        List<Tutor> tutorsWithSubjects = tutorRepository.findAll().stream()
                .filter(t -> t.getSubjectIds() != null && !t.getSubjectIds().isEmpty())
                .toList();

        if (tutorsWithSubjects.isEmpty()) {
            log.info("No tutor-subject relationships found to sync.");
            return;
        }

        tutorsWithSubjects.forEach(t -> graphSyncService.syncTutorSubjects(
                t.getId(),
                t.getSubjectIds().stream().toList()
        ));

        int relationshipCount = tutorsWithSubjects.stream()
                .mapToInt(t -> t.getSubjectIds().size())
                .sum();
        log.info("Synced {} tutor-subject relationships for {} tutors.",
                relationshipCount, tutorsWithSubjects.size());
    }

    /**
     * Bulk syncs all ClassRoom nodes (with COVERS → Topic relationships) to Neo4j.
     */
    public void syncAllClasses() {
        List<ClassRoomSyncRequest> requests = classRoomRepository.findAll().stream()
                .map(c -> ClassRoomSyncRequest.builder()
                        .id(c.getId())
                        .name(c.getName())
                        .status(c.getStatus() != null ? c.getStatus().name() : null)
                        .format(c.getFormat() != null ? c.getFormat().name() : null)
                        .topicId(c.getTopic() != null ? c.getTopic().getId() : null)
                        .build())
                .toList();

        if (requests.isEmpty()) {
            log.info("No classrooms found to sync.");
            return;
        }

        graphSyncService.syncAllClassRooms(requests);
        log.info("Bulk synced {} classrooms to Neo4j.", requests.size());
    }

    /**
     * Bulk syncs all APPROVED Enrollment → ENROLLED_IN relationships to Neo4j.
     * Only APPROVED enrollments are synced — PENDING/REJECTED/CANCELLED are excluded.
     */
    public void syncAllEnrollments() {
        List<EnrollmentSyncRequest> requests = enrollmentRepository.findAll().stream()
                .filter(e -> e.getStatus() == EnrollmentStatus.APPROVED)
                .map(e -> EnrollmentSyncRequest.builder()
                        .studentId(e.getStudentProfileId())
                        .classId(e.getClassRoom().getId())
                        .build())
                .toList();

        if (requests.isEmpty()) {
            log.info("No approved enrollments found to sync.");
            return;
        }

        graphSyncService.syncAllEnrollments(requests);
        log.info("Bulk synced {} approved enrollments to Neo4j.", requests.size());
    }

    /**
     * Full reconciliation — runs all sync operations in dependency order:
     * Subjects → Topics → Classes → Enrollments → TutorSubjects
     */
    public void syncAll() {
        log.info("Starting full Neo4j reconciliation...");
        syncAllMetadata();
        syncAllClasses();
        syncAllEnrollments();
        syncAllTutorSubjects();
        log.info("Full Neo4j reconciliation completed.");
    }

    private void syncSubjects() {
        List<SubjectSyncRequest> subjects = subjectRepository.findAll().stream()
                .map(s -> SubjectSyncRequest.builder()
                        .id(s.getId())
                        .name(s.getName())
                        .build())
                .toList();

        if (subjects.isEmpty()) {
            log.info("No subjects found to sync.");
            return;
        }

        graphSyncService.syncSubjects(subjects);
        log.info("Synced {} subjects to Neo4j.", subjects.size());
    }

    private void syncTopics() {
        List<TopicSyncRequest> topics = topicRepository.findAll().stream()
                .map(t -> TopicSyncRequest.builder()
                        .id(t.getId())
                        .name(t.getName())
                        .subjectId(t.getSubject().getId())
                        .build())
                .toList();

        if (topics.isEmpty()) {
            log.info("No topics found to sync.");
            return;
        }

        graphSyncService.syncTopics(topics);
        log.info("Synced {} topics to Neo4j.", topics.size());
    }
}
