package vn.edu.hcmut.identity.repository;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmut.identity.entity.VerificationToken;

@Repository
public interface VerificationTokenRepository extends JpaRepository<VerificationToken, String> {
    Optional<VerificationToken> findByTokenAndTypeAndUsedFalse(String token, VerificationToken.TokenType type);

    Optional<VerificationToken> findByTokenAndAccountIdAndTypeAndUsedFalse(
            String token, String accountId, VerificationToken.TokenType type);

    @Query("SELECT COUNT(v) FROM VerificationToken v " +
            "WHERE v.accountId = :accountId AND v.type = :type " +
            "AND v.used = false AND v.expiresAt > :now")
    long countActiveByAccountIdAndType(@Param("accountId") String accountId,
                                       @Param("type") VerificationToken.TokenType type,
                                       @Param("now") Instant now);

    /**
     * Deletes expired tokens AND used tokens older than the cutoff.
     * Used tokens are also removed because they're never used again.
     */
    @Transactional
    @Modifying
    @Query("DELETE FROM VerificationToken v WHERE v.expiresAt < :cutoff OR v.used = true")
    int deleteExpiredOrUsed(@Param("cutoff") Instant cutoff);
}
