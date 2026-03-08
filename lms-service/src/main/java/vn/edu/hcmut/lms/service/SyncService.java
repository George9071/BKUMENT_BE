package vn.edu.hcmut.lms.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import vn.edu.hcmut.lms.dto.sync.SubjectSyncRequest;
import vn.edu.hcmut.lms.dto.sync.TopicSyncRequest;
import vn.edu.hcmut.lms.dto.sync.TutorSubjectSyncRequest;
import vn.edu.hcmut.lms.repository.SubjectRepository;
import vn.edu.hcmut.lms.repository.TopicRepository;
import vn.edu.hcmut.lms.repository.TutorRepository;
import vn.edu.hcmut.lms.repository.httpclient.ProfileClient;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SyncService {
    private final SubjectRepository subjectRepository;
    private final TopicRepository topicRepository;
    private final TutorRepository tutorRepository;
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
}
