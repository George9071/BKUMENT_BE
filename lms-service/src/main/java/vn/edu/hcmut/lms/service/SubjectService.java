package vn.edu.hcmut.lms.service;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import vn.edu.hcmut.lms.dto.response.PageResponse;
import vn.edu.hcmut.lms.dto.response.SubjectResponse;
import vn.edu.hcmut.lms.dto.response.TopicResponse;
import vn.edu.hcmut.lms.entity.Subject;
import vn.edu.hcmut.lms.entity.Topic;
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

	public PageResponse<SubjectResponse> getAllSubjects(String q, int page, int size) {
        Pageable pageable = PageRequest.of((page > 0) ? page - 1 : 0, size);

        Page<Subject> subjectPage;
        
        if (q == null || q.isBlank()) {
            subjectPage = subjectRepository.findAllWithTopics(pageable);
        } else {
            subjectPage = subjectRepository.findByNameContainingIgnoreCaseWithTopics(q, pageable);
        }

        List<SubjectResponse> responses = subjectPage.getContent().stream()
                .map(subjectMapper::toSubjectResponse)
                .toList();

        return PageResponse.<SubjectResponse>builder()
                .currentPage(page)
                .totalPages(subjectPage.getTotalPages())
                .pageSize(subjectPage.getSize())
                .totalElements(subjectPage.getTotalElements())
                .data(responses)
                .build();
    }

    public PageResponse<TopicResponse> getAllTopicsBySubject(String subjectId, int page, int size) {
        Pageable pageable = PageRequest.of((page > 0) ? page - 1 : 0, size);

        Page<Topic> topicPage = topicRepository.findBySubjectId(subjectId, pageable);

        List<TopicResponse> responses = topicPage.getContent().stream()
                .map(subjectMapper::toTopicResponse)
                .toList();

        return PageResponse.<TopicResponse>builder()
                .currentPage(page)
                .totalPages(topicPage.getTotalPages())
                .pageSize(topicPage.getSize())
                .totalElements(topicPage.getTotalElements())
                .data(responses)
                .build();
    }
}
