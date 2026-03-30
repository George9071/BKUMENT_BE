package vn.edu.hcmut.lms.service;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import vn.edu.hcmut.lms.exception.AppException;
import vn.edu.hcmut.lms.exception.ErrorCode;
import vn.edu.hcmut.lms.repository.httpclient.IdentityClient;
import vn.edu.hcmut.lms.repository.httpclient.ProfileClient;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class TutorSyncService {

    /** Consider Message Queue / Saga Pattern for the future */

    IdentityClient identityClient;
    ProfileClient profileClient;

    public void grantTutorRole(String profileId, List<String> subjectIds) {
        var profile = profileClient.getProfile(profileId).getResult();

        try {
            identityClient.addRole(profile.getAccountId(), "TUTOR");
        } catch (Exception e) {
            throw new RuntimeException("Identity Service authorization error", e);
        }
        try {
            profileClient.addRole(profileId, "TUTOR");
            if (subjectIds != null && !subjectIds.isEmpty()) {
                profileClient.updateTutorSubjects(profileId, new HashSet<>(subjectIds));
            }
        } catch (Exception e) {
            throw new RuntimeException("Profile Service sync error", e);
        }
    }

    public void revokeTutorRole(String profileId) {
        var profile = profileClient.getProfile(profileId).getResult();

        try {
            identityClient.removeRole(profile.getAccountId(), "TUTOR");
        } catch (Exception e) {
            log.error("Failed to revoke TUTOR role from Identity Service. AccountId: {}", profile.getAccountId(), e);
            throw new RuntimeException("Identity Service authorization error", e);
        }

        try {
            profileClient.removeRole(profileId, "TUTOR");
        } catch (Exception e) {
            log.error("Failed to revoke TUTOR role from Profile Service. ProfileId: {}", profileId, e);
            throw new RuntimeException("Profile Service sync error", e);
        }
    }

    public void syncTutorSubjects(String profileId, Set<String> subjectIds) {
        try {
            profileClient.updateTutorSubjects(profileId, subjectIds);
        } catch (Exception e) {
            log.error("Failed to sync tutor subjects to Profile Service for user {}", profileId, e);
            throw new AppException(ErrorCode.SYNC_FAILED);
        }
    }
}
