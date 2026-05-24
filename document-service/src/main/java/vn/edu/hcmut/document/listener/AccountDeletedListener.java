package vn.edu.hcmut.document.listener;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import vn.edu.hcmut.document.service.DocumentService;
import vn.edu.hcmut.event.AccountDeletedEvent;

/**
 * Removes all documents (and their MinIO assets) owned by a deleted account.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AccountDeletedListener {

    DocumentService documentService;

    @KafkaListener(topics = "account-deleted-events", groupId = "document-service")
    public void onAccountDeleted(AccountDeletedEvent event) {
        log.info("[document] Account deleted event received: profileId={}", event.getProfileId());

        if (event.getProfileId() == null) {
            log.warn("[document] profileId is null in event; skipping");
            return;
        }

        try {
            documentService.deleteByOwnerId(event.getProfileId());
            log.info("[document] Deleted documents for owner {}", event.getProfileId());
        } catch (Exception e) {
            log.error("[document] Failed to delete documents for {}: {}",
                    event.getProfileId(), e.getMessage());
        }
    }
}
