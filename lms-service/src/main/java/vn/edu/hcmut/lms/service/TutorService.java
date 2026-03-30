package vn.edu.hcmut.lms.service;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmut.lms.dto.request.TutorUpdateRequest;
import vn.edu.hcmut.lms.dto.response.PageResponse;
import vn.edu.hcmut.lms.dto.response.SubjectResponse;
import vn.edu.hcmut.lms.dto.response.TutorResponse;
import vn.edu.hcmut.lms.entity.Subject;
import vn.edu.hcmut.lms.entity.Tutor;
import vn.edu.hcmut.lms.exception.AppException;
import vn.edu.hcmut.lms.exception.ErrorCode;
import vn.edu.hcmut.lms.mapper.SubjectMapper;
import vn.edu.hcmut.lms.mapper.TutorMapper;
import vn.edu.hcmut.lms.repository.SubjectRepository;
import vn.edu.hcmut.lms.repository.TutorRepository;
import vn.edu.hcmut.lms.utils.SecurityUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class TutorService {
    TutorRepository tutorRepository;
    SubjectRepository subjectRepository;
    SubjectMapper subjectMapper;
    TutorMapper tutorMapper;
    TutorSyncService syncService;
    SecurityUtils utils;

    @Transactional(rollbackFor = Exception.class)
    public TutorResponse updateTutorProfile(TutorUpdateRequest request) {
        String profileId = utils.getProfileId();

        Tutor tutor = tutorRepository.findById(profileId)
                .orElseThrow(() -> new AppException(ErrorCode.TUTOR_NOT_FOUND));

        tutorMapper.updateTutor(tutor, request);

        if (request.getSubjectIds() != null) {
            syncService.syncTutorSubjects(profileId, request.getSubjectIds());
        }

        tutor = tutorRepository.save(tutor);
        return tutorMapper.toResponse(tutor);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteTutor(String profileId) {
        if (tutorRepository.existsById(profileId)) {
            tutorRepository.deleteById(profileId);
            syncService.revokeTutorRole(profileId);
            log.info("Deleted tutor profile for id: {}", profileId);
        }
    }

    public PageResponse<TutorResponse> getTutors(String subjectId, int page, int size) {
        Pageable pageable = PageRequest.of((page > 0) ? page - 1 : 0, size);
        Page<Tutor> tutors;

        if (subjectId != null && !subjectId.isBlank()) tutors = tutorRepository.findBySubjectId(subjectId, pageable);
        else tutors = tutorRepository.findByStatus("ACTIVE", pageable);

        List<TutorResponse> responses = tutors.getContent().stream()
                .map(tutorMapper::toResponse)
                .toList();

        return PageResponse.<TutorResponse>builder()
                .currentPage(page)
                .totalPages(tutors.getTotalPages())
                .pageSize(tutors.getSize())
                .totalElements(tutors.getTotalElements())
                .data(responses)
                .build();
    }

    @Transactional(readOnly = true)
    public PageResponse<SubjectResponse> getTutorSubjects(int page, int size) {
        String id = utils.getProfileId();
        Tutor tutor = tutorRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.TUTOR_NOT_FOUND));

        Set<String> subjectIds = tutor.getSubjectIds();

        if (subjectIds == null || subjectIds.isEmpty()) {
            return PageResponse.<SubjectResponse>builder()
                    .currentPage(page)
                    .totalPages(0)
                    .pageSize(size)
                    .totalElements(0L)
                    .data(new ArrayList<>())
                    .build();
        }

        Pageable pageable = PageRequest.of((page > 0) ? page - 1 : 0, size);

        Page<Subject> subjectPage = subjectRepository.findByIdIn(subjectIds, pageable);

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

    public TutorResponse getMyTutorProfile() {
        String tutorId = utils.getProfileId();

        Tutor tutor = tutorRepository.findById(tutorId)
                .orElseThrow(() -> new AppException(ErrorCode.TUTOR_NOT_FOUND));

        return TutorResponse.builder()
                .id(tutor.getId())
                .introduction(tutor.getIntroduction())
                .averageRating(tutor.getAverageRating())
                .ratingCount(tutor.getRatingCount())
                .status(tutor.getStatus())
                .name(tutor.getName())
                .avatar(tutor.getAvatar())
                .build();
    }
}
