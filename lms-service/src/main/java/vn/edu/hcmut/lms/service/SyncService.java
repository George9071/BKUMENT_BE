package vn.edu.hcmut.lms.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import vn.edu.hcmut.lms.constant.EnrollmentStatus;
import vn.edu.hcmut.lms.dto.sync.*;
import vn.edu.hcmut.lms.entity.ClassRoom;
import vn.edu.hcmut.lms.entity.Enrollment;
import vn.edu.hcmut.lms.repository.*;
import vn.edu.hcmut.lms.repository.httpclient.ProfileClient;

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
    private final ProfileClient profileClient;

    public void syncAllMetadata() {
        List<SubjectSyncRequest> subjects =  subjectRepository
                .findAll()
                .stream()
                .map(s -> SubjectSyncRequest.builder()
                        .id(s.getId())
                        .name(s.getName())
                        .build())
                .toList();

        if (!subjects.isEmpty()) {
            profileClient.syncSubjects(subjects);
            log.info("Successfully requested sync for {} subjects", subjects.size());
        }

        List<TopicSyncRequest> topics = topicRepository
                .findAll()
                .stream()
                .map(t -> TopicSyncRequest.builder()
                        .id(t.getId())
                        .name(t.getName())
                        .subjectId(t.getSubject().getId())
                        .build())
                .toList();

        if (!topics.isEmpty()) {
            profileClient.syncTopics(topics);
            log.info("Successfully requested sync for {} topics", topics.size());
        }
    }

    public void syncAllTutorSubjects() {
        var tutors = tutorRepository.findAll();

        List<TutorSubjectSyncRequest> requests = tutors.stream()
                .filter(t -> t.getSubjectIds() != null && !t.getSubjectIds().isEmpty())
                .map(t -> TutorSubjectSyncRequest.builder()
                        .tutorId(t.getId())
                        .subjectIds(t.getSubjectIds())
                        .build())
                .toList();

        if (!requests.isEmpty()) {
            profileClient.syncTutorSubjects(requests);
            log.info("Requested sync for {} tutors and their subjects", requests.size());
        }
    }

    public void syncAllClasses() {
        List<ClassRoom> classes = classRoomRepository.findAll();

        List<ClassRoomSyncRequest> requests = classes.stream()
                .map(c -> ClassRoomSyncRequest.builder()
                        .id(c.getId())
                        .name(c.getName())
                        .status(c.getStatus() != null ? c.getStatus().name() : null)
                        .format(c.getFormat() != null ? c.getFormat().name() : null)
                        .topicId(c.getTopic() != null ? c.getTopic().getId() : null)
                        .build())
                .toList();

        if (!requests.isEmpty()) {
            profileClient.syncClasses(requests);
            log.info("Requested bulk sync for {} classes to Neo4j", requests.size());
        } else {
            log.info("No class found in JPA to sync.");
        }
    }

    public void syncAllEnrollments() {
        List<Enrollment> enrollments = enrollmentRepository.findAll();

        List<EnrollmentSyncRequest> requests = enrollments.stream()
                .filter(e -> e.getStatus() == EnrollmentStatus.APPROVED)
                .map(e -> EnrollmentSyncRequest.builder()
                        .studentId(e.getStudentProfileId())
                        .classId(e.getClassRoom().getId())
                        .build())
                .toList();

        if (!requests.isEmpty()) {
            profileClient.syncAllEnrollments(requests);
            log.info("Requested bulk sync for {} Enrollments to Neo4j", requests.size());
        } else {
            log.info("No approved enrollments found in Postgres to sync.");
        }
    }
}
