package vn.edu.hcmut.profile.listener;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import vn.edu.hcmut.event.AccountDeletedEvent;
import vn.edu.hcmut.profile.service.ProfileService;

/**
 * Cleans up profile data (JPA + Neo4j) when an account is deleted in identity-service.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AccountDeletedListener {

    ProfileService profileService;

    @KafkaListener(topics = "account-deleted-events", groupId = "profile-service")
    public void onAccountDeleted(AccountDeletedEvent event) {
        log.info("[profile] Account deleted event received: accountId={}, profileId={}",
                event.getAccountId(), event.getProfileId());

        if (event.getProfileId() == null) {
            log.warn("[profile] profileId is null in event; nothing to delete");
            return;
        }

        try {
            profileService.deleteProfile(event.getProfileId());
            log.info("[profile] Deleted profile {}", event.getProfileId());
        } catch (Exception e) {
            log.error("[profile] Failed to delete profile {}: {}",
                    event.getProfileId(), e.getMessage());
        }
    }
}
