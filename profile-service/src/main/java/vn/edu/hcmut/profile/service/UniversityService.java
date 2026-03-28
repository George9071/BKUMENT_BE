package vn.edu.hcmut.profile.service;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import vn.edu.hcmut.profile.dto.response.PageResponse;
import vn.edu.hcmut.profile.dto.response.UniversityResponse;
import vn.edu.hcmut.profile.entity.jpa.University;
import vn.edu.hcmut.profile.mapper.UniversityMapper;
import vn.edu.hcmut.profile.repository.UniversityRepository;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class UniversityService {
    UniversityRepository universityRepository;
    UniversityMapper universityMapper;

    public List<UniversityResponse> getAllUniversities() {
        return universityRepository.findAll().stream()
                .map(universityMapper::toResponse)
                .toList();
    }

    /**
     * Searches for universities by name or abbreviation with pagination.
     * If the query is empty, returns a paginated list of all universities.
     *
     * @param query The search keyword (optional).
     * @param page  The requested page number (1-based index from frontend).
     * @param size  The number of records per page.
     * @return      A paginated response containing a list of UniversityResponse.
     */
    public PageResponse<UniversityResponse> searchUniversities(String query, int page, int size) {
        Pageable pageable = PageRequest.of((page > 0) ? page - 1 : 0, size);
        Page<University> universities;

        if (query == null || query.trim().isEmpty()) {
            universities = universityRepository.findAll(pageable);
        } else {
            String keyword = query.trim();
            universities = universityRepository.search(keyword, pageable);
        }

        var responses = universities.getContent().stream()
                .map(universityMapper::toResponse)
                .toList();

        return PageResponse.<UniversityResponse>builder()
                .currentPage(page)
                .totalPages(universities.getTotalPages())
                .pageSize(universities.getSize())
                .totalElements(universities.getTotalElements())
                .data(responses)
                .build();
    }
}
