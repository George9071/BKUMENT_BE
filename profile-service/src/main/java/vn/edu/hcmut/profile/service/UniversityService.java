package vn.edu.hcmut.profile.service;

import org.springframework.stereotype.Service;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import vn.edu.hcmut.profile.repository.UniversityRepository;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class UniversityService {
    UniversityRepository universityRepository;

    //    public List<UniversityResponse> getAllUniversities() {
    //        return universityRepository.findAll().stream()
    //                .map(uniMapper::toResponse)
    //                .toList();
    //    }
}
