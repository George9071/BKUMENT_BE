package vn.edu.hcmut.identity.repository;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import vn.edu.hcmut.identity.entity.VerificationToken;

@Repository
public interface VerificationTokenRepository extends JpaRepository<VerificationToken, String> {
    Optional<VerificationToken> findByTokenAndTypeAndUsedFalse(String token, VerificationToken.TokenType type);

    void deleteByExpiresAtBefore(Instant now);
}
