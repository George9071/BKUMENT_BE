package vn.edu.hcmut.identity.scheduler;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmut.identity.repository.InvalidatedTokenRepository;
import vn.edu.hcmut.identity.repository.VerificationTokenRepository;

import java.time.Instant;
import java.util.Date;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class TokenCleanupScheduler {

    InvalidatedTokenRepository invalidatedTokenRepository;
    VerificationTokenRepository verificationTokenRepository;

    @Scheduled(cron = "0 0 3 * * *", zone = "Asia/Ho_Chi_Minh")
    public void cleanupInvalidatedTokens() {
        int deleted = invalidatedTokenRepository.deleteByExpiryTimeBefore(new Date());
        log.info("Cleanup: removed {} expired invalidated-token rows", deleted);
    }

    @Scheduled(cron = "0 15 3 * * *", zone = "Asia/Ho_Chi_Minh")
    @Transactional
    public void cleanupVerificationTokens() {
        int deleted = verificationTokenRepository.deleteExpiredOrUsed(Instant.now());
        log.info("Cleanup: removed {} expired/used verification-token rows", deleted);
    }
}
