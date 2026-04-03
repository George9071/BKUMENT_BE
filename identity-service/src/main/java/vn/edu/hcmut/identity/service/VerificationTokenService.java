package vn.edu.hcmut.identity.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Random;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import vn.edu.hcmut.identity.entity.VerificationToken;
import vn.edu.hcmut.identity.exception.AppException;
import vn.edu.hcmut.identity.exception.ErrorCode;
import vn.edu.hcmut.identity.repository.VerificationTokenRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class VerificationTokenService {

    @Value("${app.base-url}")
    private String baseUrl;

    @Value("${app.fe-url:http://localhost:3000}")
    private String frontendUrl;

    private static final long EMAIL_VERIFY_EXPIRY_HOURS = 24;
    private static final long PASSWORD_RESET_EXPIRY_HOURS = 1;

    private final VerificationTokenRepository tokenRepository;

    public String createEmailVerificationLink(String accountId) {
        String token = generateToken();
        tokenRepository.save(VerificationToken.builder()
                .token(token)
                .accountId(accountId)
                .type(VerificationToken.TokenType.EMAIL_VERIFICATION)
                .expiresAt(Instant.now().plus(EMAIL_VERIFY_EXPIRY_HOURS, ChronoUnit.HOURS))
                .used(false)
                .build());

        return frontendUrl + "/verify-email?token=" + token;
    }

    public String createPasswordResetOtp(String accountId) {
        String otp = String.format("%06d", new Random().nextInt(999999));

        tokenRepository.save(VerificationToken.builder()
                .token(otp)
                .accountId(accountId)
                .type(VerificationToken.TokenType.PASSWORD_RESET)
                .expiresAt(Instant.now().plus(PASSWORD_RESET_EXPIRY_HOURS, ChronoUnit.HOURS))
                .used(false)
                .build());
        return otp;
    }

    public VerificationToken validateToken(String token, VerificationToken.TokenType type) {
        VerificationToken vToken = tokenRepository
                .findByTokenAndTypeAndUsedFalse(token, type)
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_VERIFICATION_TOKEN));

        if (vToken.getExpiresAt().isBefore(Instant.now())) {
            throw new AppException(ErrorCode.VERIFICATION_TOKEN_EXPIRED);
        }

        return vToken;
    }

    public void markAsUsed(VerificationToken token) {
        token.setUsed(true);
        tokenRepository.save(token);
    }

    private String generateToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
