package vn.edu.hcmut.lms.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import vn.edu.hcmut.lms.dto.sync.SubjectSyncRequest;
import vn.edu.hcmut.lms.dto.sync.TopicSyncRequest;
import vn.edu.hcmut.lms.entity.Subject;
import vn.edu.hcmut.lms.entity.Topic;
import vn.edu.hcmut.lms.repository.SubjectRepository;
import vn.edu.hcmut.lms.repository.TopicRepository;
import vn.edu.hcmut.lms.repository.httpclient.ProfileClient;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SyncService {
    private final SubjectRepository subjectRepository;
    private final TopicRepository topicRepository;
    private final ProfileClient profileClient;

    public void syncSubjects() {
        List<String> targetSubjectIds = List.of("AI1014", "INT1005");

        List<Subject> subjects = subjectRepository.findAllById(targetSubjectIds);

        // Map sang DTO
        List<SubjectSyncRequest> subjectDtos = subjects.stream()
                .map(s -> SubjectSyncRequest.builder()
                        .id(s.getId())
                        .name(s.getName())
                        .build())
                .toList();

        // Gửi sang Profile Service (Neo4j)
        if (!subjectDtos.isEmpty()) {
            profileClient.syncSubjects(subjectDtos);
            System.out.println("Synced Subjects: " + targetSubjectIds);
        }

        // 3. Lấy Topic thuộc các Subject này từ Postgres
        // Bạn cần viết thêm hàm findBySubjectIdIn trong TopicRepository
        List<Topic> topics = topicRepository.findBySubjectIdIn(targetSubjectIds);

        // Map sang DTO
        List<TopicSyncRequest> topicDtos = topics.stream()
                .map(t -> TopicSyncRequest.builder()
                        .id(t.getId())
                        .name(t.getName())
                        .subjectId(t.getSubject().getId())
                        .build())
                .toList();

        // Gửi sang Profile Service (Neo4j)
        if (!topicDtos.isEmpty()) {
            profileClient.syncTopics(topicDtos);
            System.out.println("Synced Topics count: " + topicDtos.size());
        }
    }
}
