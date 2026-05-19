package vn.edu.hcmut.blog.listener;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import vn.edu.hcmut.blog.service.PostService;
import vn.edu.hcmut.event.AccountDeletedEvent;

/**
 * Removes all posts (and their attached MinIO assets, social data) owned by a deleted account.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AccountDeletedListener {

    PostService postService;

    @KafkaListener(topics = "account-deleted-events", groupId = "blog-service")
    public void onAccountDeleted(AccountDeletedEvent event) {
        log.info("[blog] Account deleted event received: profileId={}", event.getProfileId());

        if (event.getProfileId() == null) {
            log.warn("[blog] profileId is null in event; skipping");
            return;
        }

        try {
            postService.deleteByOwnerId(event.getProfileId());
            log.info("[blog] Deleted posts for owner {}", event.getProfileId());
        } catch (Exception e) {
            log.error("[blog] Failed to delete posts for {}: {}",
                    event.getProfileId(), e.getMessage());
        }
    }
}
