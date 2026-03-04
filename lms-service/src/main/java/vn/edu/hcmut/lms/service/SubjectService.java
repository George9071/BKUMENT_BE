package vn.edu.hcmut.lms.service;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import vn.edu.hcmut.lms.dto.response.SubjectResponse;
import vn.edu.hcmut.lms.dto.response.TopicResponse;
import vn.edu.hcmut.lms.mapper.SubjectMapper;
import vn.edu.hcmut.lms.repository.SubjectRepository;
import vn.edu.hcmut.lms.repository.TopicRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SubjectService {
    SubjectRepository subjectRepository;
    TopicRepository topicRepository;
    SubjectMapper subjectMapper;

    public List<SubjectResponse> getAllSubjects() {
        return subjectRepository.findAllWithTopics().stream()
                .map(subjectMapper::toSubjectResponse)
                .toList();
    }

    public List<TopicResponse> getAllTopicsBySubject(String subjectId) {
        return topicRepository.findBySubjectId(subjectId).stream()
                .map(subjectMapper::toTopicResponse)
                .toList();
    }


}
