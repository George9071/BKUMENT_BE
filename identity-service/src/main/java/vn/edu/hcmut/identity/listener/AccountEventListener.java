package vn.edu.hcmut.identity.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import vn.edu.hcmut.event.AccountDeletedEvent;

@Component
@RequiredArgsConstructor
@Slf4j
public class AccountEventListener {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleAccountDeletedEvent(AccountDeletedEvent event) {
        try {
            kafkaTemplate.send("account-deleted-events", event);
            log.info("Kafka message published for account {}", event.getAccountId());
        } catch (Exception e) {
            log.error("CRITICAL: Failed to publish to Kafka after DB commit for account {}", event.getAccountId(), e);
        }
    }
}
