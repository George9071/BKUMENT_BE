package vn.edu.hcmut.lms.service;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmut.lms.dto.request.TutorRegistrationRequest;
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
import vn.edu.hcmut.lms.repository.httpclient.IdentityClient;
import vn.edu.hcmut.lms.repository.httpclient.ProfileClient;

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
    ProfileClient profileClient;
    IdentityClient identityClient;

    /**
     * Register to become a tutor.
     * WARNING: calling multiple FeignClients in one Transaction.
     * Consider Message Queue / Saga Pattern for the future
     */
    @Transactional(rollbackFor = Exception.class)
    public TutorResponse registerTutor(TutorRegistrationRequest request) {
        String profileId = getProfileIdFromToken();
        if (tutorRepository.existsById(profileId)) {
            throw new AppException(ErrorCode.TUTOR_ALREADY_REGISTERED);
        }

        var user = profileClient.getProfile(profileId);
        if (user == null) throw new AppException(ErrorCode.PROFILE_NOT_FOUND);

        Tutor tutor = tutorMapper.toTutor(request);
        tutor.setId(profileId);
        tutor.setNew(true);
        tutorRepository.save(tutor);

        try {
            identityClient.addRole(getAccountIdFromToken(), "TUTOR");
        } catch (Exception e) {
            throw new RuntimeException("Failed to sync role to Identity Service", e);
        }

        try {
            profileClient.addRole(profileId, "TUTOR");
            if (request.getSubjectIds() != null && !request.getSubjectIds().isEmpty()) {
                profileClient.updateTutorSubjects(profileId, request.getSubjectIds());
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to sync role to Profile Service", e);
        }

        return tutorMapper.toResponse(tutor);
    }

    @Transactional(rollbackFor = Exception.class)
    public TutorResponse updateTutorProfile(TutorUpdateRequest request) {
        String profileId = getProfileIdFromToken();

        Tutor tutor = tutorRepository.findById(profileId)
                .orElseThrow(() -> new AppException(ErrorCode.TUTOR_NOT_FOUND));

        tutorMapper.updateTutor(tutor, request);

        if (request.getSubjectIds() != null) {
            try {
                profileClient.updateTutorSubjects(profileId, request.getSubjectIds());
            } catch (Exception e) {
                log.error("Failed to sync tutor subjects to Profile Service for user {}", profileId, e);
                throw new AppException(ErrorCode.SYNC_FAILED);
            }
        }

        tutor = tutorRepository.save(tutor);
        return tutorMapper.toResponse(tutor);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteTutor(String profileId) {
        if (tutorRepository.existsById(profileId)) {
            tutorRepository.deleteById(profileId);
            log.info("Deleted Tutor profile for id: {}", profileId);

            try {
                var profile = profileClient.getProfile(profileId).getResult();

                if (profile != null && profile.getAccountId() != null) {
                    String accountId = profile.getAccountId();
                    identityClient.removeRole(accountId, "TUTOR");
                } else {
                    log.warn("Could not find accountId for profile: {}", profileId);
                    throw new AppException(ErrorCode.PROFILE_NOT_FOUND);
                }
                profileClient.removeRole(profileId, "TUTOR");

            } catch (Exception e) {
                log.error("Failed to remove tutor role for user {} in external services", profileId, e);
                throw new AppException(ErrorCode.SYNC_FAILED);
            }
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
        String id = getProfileIdFromToken();
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
        String tutorId = getProfileIdFromToken();

        Tutor tutor = tutorRepository.findById(tutorId)
                .orElseThrow(() -> new AppException(ErrorCode.TUTOR_NOT_FOUND));

        return TutorResponse.builder()
                .id(tutor.getId())
                .introduction(tutor.getIntroduction())
                .averageRating(tutor.getAverageRating())
                .ratingCount(tutor.getRatingCount())
                .status(tutor.getStatus()) // Assuming status is an Enum
                .name(tutor.getName())
                .avatar(tutor.getAvatar())
                .build();
    }

    private String getProfileIdFromToken() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) throw new AppException(ErrorCode.UNAUTHENTICATED);

        var principal = authentication.getPrincipal();
        if (principal instanceof Jwt jwt) {

            String profileId = jwt.getClaimAsString("profile_id");
            if (profileId == null) throw new AppException(ErrorCode.INVALID_TOKEN_CLAIMS);
            return profileId;
        }
        throw new AppException(ErrorCode.UNAUTHENTICATED);
    }

    private String getAccountIdFromToken() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) throw new AppException(ErrorCode.UNAUTHENTICATED);

        if (authentication.getPrincipal() instanceof Jwt jwt) {
            return jwt.getSubject();
        }
        throw new AppException(ErrorCode.UNAUTHENTICATED);
    }
}
