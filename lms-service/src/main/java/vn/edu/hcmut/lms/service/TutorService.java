package vn.edu.hcmut.lms.service;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmut.lms.dto.request.TutorRegistrationRequest;
import vn.edu.hcmut.lms.dto.request.TutorUpdateRequest;
import vn.edu.hcmut.lms.dto.response.TutorResponse;
import vn.edu.hcmut.lms.entity.Tutor;
import vn.edu.hcmut.lms.exception.AppException;
import vn.edu.hcmut.lms.exception.ErrorCode;
import vn.edu.hcmut.lms.mapper.TutorMapper;
import vn.edu.hcmut.lms.repository.TutorRepository;
import vn.edu.hcmut.lms.repository.httpclient.ProfileClient;

import java.util.List;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class TutorService {
    TutorRepository tutorRepository;
    TutorMapper tutorMapper;
    ProfileClient profileClient;

    @Transactional
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
            profileClient.addRole(profileId, "TUTOR");
            if (request.getSubjectIds() != null && !request.getSubjectIds().isEmpty()) {
                profileClient.updateTutorSubjects(profileId, request.getSubjectIds());
            }
        } catch (Exception e) {
            log.error("Failed to sync role to Profile Service", e);
        }

        return tutorMapper.toResponse(tutor);
    }

    @Transactional
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

    public List<TutorResponse> getTutors(String subjectId) {
        List<Tutor> tutors;

        if (subjectId != null && !subjectId.isBlank()) {
            tutors = tutorRepository.findBySubjectId(subjectId);
        } else {
            tutors = tutorRepository.findByStatus("ACTIVE");
        }

        return tutors.stream()
                .map(tutorMapper::toResponse)
                .toList();
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
}
