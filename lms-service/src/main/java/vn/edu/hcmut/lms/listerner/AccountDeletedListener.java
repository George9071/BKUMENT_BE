package vn.edu.hcmut.lms.listerner;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import vn.edu.hcmut.event.AccountDeletedEvent;
import vn.edu.hcmut.lms.repository.EnrollmentRepository;
import vn.edu.hcmut.lms.service.EnrollmentService;
import vn.edu.hcmut.lms.service.TutorService;

/**
 * Removes tutor profile + classes + enrollments when an account is deleted.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AccountDeletedListener {

    TutorService tutorService;

    @KafkaListener(topics = "account-deleted-events", groupId = "lms-service")
    public void onAccountDeleted(AccountDeletedEvent event) {
        log.info("[lms] Account deleted event received: profileId={}", event.getProfileId());

        if (event.getProfileId() == null) {
            log.warn("[lms] profileId is null in event; skipping");
            return;
        }

        try {
            tutorService.deleteTutor(event.getProfileId());
            log.info("[lms] Cleaned LMS data for profile {}", event.getProfileId());
        } catch (Exception e) {
            log.error("[lms] Failed to clean LMS data for {}: {}",
                    event.getProfileId(), e.getMessage());
        }
    }
}
