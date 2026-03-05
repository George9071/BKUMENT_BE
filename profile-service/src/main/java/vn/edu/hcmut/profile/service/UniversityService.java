package vn.edu.hcmut.profile.service;

import java.util.List;

import org.springframework.stereotype.Service;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import vn.edu.hcmut.profile.dto.response.UniversityResponse;
import vn.edu.hcmut.profile.mapper.UniversityMapper;
import vn.edu.hcmut.profile.repository.UniversityRepository;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class UniversityService {
    UniversityRepository universityRepository;
    UniversityMapper universityMapper;

    public List<UniversityResponse> searchUniversities(String query) {
        if (query == null || query.trim().isEmpty()) {
            return universityRepository.findAll().stream()
                    .map(universityMapper::toResponse)
                    .toList();
        }

        return universityRepository
                .findByNameContainingIgnoreCaseOrAbbreviationContainingIgnoreCase(query, query)
                .stream()
                .map(universityMapper::toResponse)
                .toList();
    }
}
