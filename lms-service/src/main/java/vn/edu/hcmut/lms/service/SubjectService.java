package vn.edu.hcmut.lms.service;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import vn.edu.hcmut.lms.dto.response.SubjectResponse;
import vn.edu.hcmut.lms.mapper.SubjectMapper;
import vn.edu.hcmut.lms.repository.SubjectRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SubjectService {
    SubjectRepository subjectRepository;
    SubjectMapper subjectMapper;

    public List<SubjectResponse> searchSubjects(String query) {
        if (query == null || query.trim().isEmpty()) {
            return subjectRepository.findAll().stream()
                    .map(subjectMapper::toSubjectResponse)
                    .toList();
        }

        return subjectRepository.findByNameContainingIgnoreCase(query).stream()
                .map(subjectMapper::toSubjectResponse)
                .toList();
    }
}
