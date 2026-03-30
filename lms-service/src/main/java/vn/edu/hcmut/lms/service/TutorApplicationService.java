package vn.edu.hcmut.lms.service;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmut.lms.constant.ApplicationStatus;
import vn.edu.hcmut.lms.dto.request.TutorRegistrationRequest;
import vn.edu.hcmut.lms.dto.response.ApplicationResponse;
import vn.edu.hcmut.lms.entity.Tutor;
import vn.edu.hcmut.lms.entity.TutorApplication;
import vn.edu.hcmut.lms.exception.AppException;
import vn.edu.hcmut.lms.exception.ErrorCode;
import vn.edu.hcmut.lms.mapper.TutorApplicationMapper;
import vn.edu.hcmut.lms.repository.TutorApplicationRepository;
import vn.edu.hcmut.lms.repository.TutorRepository;
import vn.edu.hcmut.lms.repository.httpclient.ProfileClient;
import vn.edu.hcmut.lms.utils.SecurityUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class TutorApplicationService {

    ProfileClient profileClient;
    SecurityUtils utils;
    TutorRepository tutorRepository;
    TutorApplicationRepository applicationRepository;
    TutorApplicationMapper mapper;
    TutorSyncService syncService;

    @Transactional(rollbackFor = Exception.class)
    public ApplicationResponse registerTutor(TutorRegistrationRequest request){
        String profileId = utils.getProfileId();

        if (tutorRepository.existsById(profileId)) {
            throw new AppException(ErrorCode.TUTOR_ALREADY_REGISTERED);
        }

        var user = profileClient.getProfile(profileId).getResult();
        if (user == null) throw new AppException(ErrorCode.PROFILE_NOT_FOUND);
        if (user.getPoints() <= 100) throw new AppException(ErrorCode.NOT_ENOUGH_POINTS);

        Optional<TutorApplication> app = applicationRepository.findByProfileId(profileId);
        TutorApplication application;

        if (app.isPresent()) {
            application = app.get();
            if (application.getStatus() == ApplicationStatus.PENDING) {
                throw new AppException(ErrorCode.REGISTRATION_PENDING);
            }

            if (application.getStatus() == ApplicationStatus.REJECTED) {
                if (isCooldownActive(application)) {
                    throw new AppException(ErrorCode.REGISTRATION_COOLDOWN);
                }

                mapper.updateEntityFromRequest(request, application);
                application.setStatus(ApplicationStatus.PENDING);
                application.setRejectionReason(null);
                application.setReviewedAt(null);
                application.setReviewedBy(null);
            }
        } else {
            application = mapper.toEntity(request);
            application.setProfileId(profileId);
            application.setStatus(ApplicationStatus.PENDING);
        }

        application = applicationRepository.save(application);
        return mapper.toResponse(application);
    }

    @Transactional(rollbackFor = Exception.class)
    public void approveApplication(String applicationId){
        String solver = utils.getAccountId();

        var application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new AppException(ErrorCode.APPLICATION_NOT_FOUND));

        if (application.getStatus() != ApplicationStatus.PENDING) {
            throw new AppException(ErrorCode.INVALID_STATUS);
        }

        application.setStatus(ApplicationStatus.APPROVED);
        application.setReviewedAt(Instant.now());
        application.setReviewedBy(solver);
        applicationRepository.save(application);

        var tutor = Tutor.builder()
                .id(application.getProfileId())
                .name(application.getName())
                .avatar(application.getAvatar())
                .introduction(application.getIntroduction())
                .experience(application.getExperience())
                .cvUrl(application.getCvUrl())
                .build();

        tutor.setNew(true);
        tutorRepository.save(tutor);

        syncService.grantTutorRole(application.getProfileId(), application.getSubjectIds());
    }

    @Transactional(rollbackFor = Exception.class)
    public void rejectApplication(String applicationId, String reason){
        String solver = utils.getAccountId();

        var application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new AppException(ErrorCode.APPLICATION_NOT_FOUND));

        if (application.getStatus() != ApplicationStatus.PENDING) {
            throw new AppException(ErrorCode.INVALID_STATUS);
        }

        application.setStatus(ApplicationStatus.REJECTED);
        application.setRejectionReason(reason);
        application.setReviewedAt(Instant.now());
        application.setReviewedBy(solver);

        applicationRepository.save(application);
    }

    @Transactional(readOnly = true)
    public Page<ApplicationResponse> getApplications(ApplicationStatus status, int page, int size){
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        Page<TutorApplication> applications;

        if (status != null) {
            applications = applicationRepository.findByStatus(status, pageable);
        } else {
            applications = applicationRepository.findAll(pageable);
        }

        return applications.map(mapper::toResponse);
    }

    private boolean isCooldownActive(TutorApplication application) {
        return application.getReviewedAt() != null &&
                application.getReviewedAt()
                        .plus(3, ChronoUnit.DAYS)
                        .isAfter(Instant.now());
    }
}
