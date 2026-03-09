package vn.edu.hcmut.identity.service;

import java.text.ParseException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import vn.edu.hcmut.identity.constant.UserRole;
import vn.edu.hcmut.identity.dto.request.AuthenticationRequest;
import vn.edu.hcmut.identity.dto.request.IntrospectRequest;
import vn.edu.hcmut.identity.dto.request.LogoutRequest;
import vn.edu.hcmut.identity.dto.request.RefreshRequest;
import vn.edu.hcmut.identity.dto.response.AuthenticationResponse;
import vn.edu.hcmut.identity.dto.response.IntrospectResponse;
import vn.edu.hcmut.identity.dto.response.ProfileResponse;
import vn.edu.hcmut.identity.entity.Account;
import vn.edu.hcmut.identity.entity.InvalidatedToken;
import vn.edu.hcmut.identity.exception.AppException;
import vn.edu.hcmut.identity.exception.ErrorCode;
import vn.edu.hcmut.identity.repository.AccountRepository;
import vn.edu.hcmut.identity.repository.InvalidatedTokenRepository;
import vn.edu.hcmut.identity.repository.httpclient.ProfileClient;

@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AuthenticationService {
    AccountRepository accountRepository;
    InvalidatedTokenRepository invalidatedTokenRepository;
    ProfileClient profileClient;
    PasswordEncoder passwordEncoder;

    @NonFinal
    @Value("${jwt.signerKey}")
    protected String SIGNER_KEY;

    @NonFinal
    @Value("${jwt.valid-duration}")
    protected long VALID_DURATION;

    @NonFinal
    @Value("${jwt.refreshable-duration}")
    protected long REFRESHABLE_DURATION;

    /**
     * Verifies whether a given token is valid and extracts the profile_id.
     * Used by API Gateway or internal checks.
     */
    public IntrospectResponse introspect(IntrospectRequest request) {
        var token = request.getToken();
        boolean isValid = true;
        SignedJWT jwt = null;

        try {
            jwt = verifyToken(token, false);
        } catch (AppException | JOSEException | ParseException e) {
            isValid = false;
        }

        return IntrospectResponse.builder()
                .valid(isValid)
                .profileId(Objects.nonNull(jwt) ? parseStringClaim(jwt, "profile_id") : null)
                .build();
    }

    /**
     * Authenticates the user credentials and issues a new JWT.
     */
    public AuthenticationResponse authenticate(AuthenticationRequest request) {
        var account = accountRepository
                .findByUsername(request.getUsername())
                .orElseThrow(() -> new AppException(ErrorCode.ACCOUNT_NOT_FOUND));

        boolean authenticated = passwordEncoder.matches(request.getPassword(), account.getPassword());

        if (!authenticated) throw new AppException(ErrorCode.UNAUTHENTICATED);

        boolean isAdmin = account.getRoles().stream()
                .anyMatch(role -> role.equals(UserRole.ADMIN));

        ProfileResponse profile = null;

        if (!isAdmin) {
            try {
                profile = profileClient.getProfileByAccountId(account.getId()).getResult();
            } catch (Exception e) {
                log.error("CRITICAL: Cannot fetch profile for account {}", account.getId(), e);
                throw new AppException(ErrorCode.PROFILE_NOT_FOUND);
            }
        }

        var token = generateToken(account, profile);

        return AuthenticationResponse.builder()
                .token(token.token)
                .authenticated(true)
                .expiryTime(token.expiryDate)
                .build();
    }

    /**
     * Invalidates a token by storing its ID (JTI) into the database.
     */
    public void logout(LogoutRequest request) {
        try {
            var signToken = verifyToken(request.getToken(), true);

            String jti = signToken.getJWTClaimsSet().getJWTID();
            Date expiryTime = signToken.getJWTClaimsSet().getExpirationTime();

            InvalidatedToken invalidatedToken = InvalidatedToken.builder()
                    .id(jti)
                    .expiryTime(expiryTime)
                    .build();

            invalidatedTokenRepository.save(invalidatedToken);
            log.info("Token {} successfully invalidated", jti);

        } catch (AppException | ParseException | JOSEException exception) {
            log.info("Token is already expired or invalid, skipping logout");
        }
    }

    /**
     * Issues a new token if the old token is within the refreshable duration.
     */
    public AuthenticationResponse refreshToken(RefreshRequest request)
            throws ParseException, JOSEException {
        var signedJWT = verifyToken(request.getToken(), true);

        // Invalidate old token
        var jti = signedJWT.getJWTClaimsSet().getJWTID();
        var expiryTime = signedJWT.getJWTClaimsSet().getExpirationTime();
        invalidatedTokenRepository.save(InvalidatedToken.builder().id(jti).expiryTime(expiryTime).build());

        // Fetch account
        var accountId = signedJWT.getJWTClaimsSet().getSubject();
        var account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));

        ProfileResponse profile;
        try {
            profile = profileClient.getProfileByAccountId(account.getId()).getResult();
        } catch (Exception e) {
            log.warn("ProfileService is down during token refresh. Falling back to claims from the old token.");
            profile = extractProfileFromOldToken(signedJWT);
        }

        // Issue new token
        var token = generateToken(account, profile);

        return AuthenticationResponse.builder()
                .token(token.token)
                .expiryTime(token.expiryDate)
                .build();
    }

    /* ========================================================================= */
    /* HELPER METHODS                                                            */
    /* ========================================================================= */
    private TokenInfo generateToken(Account account, ProfileResponse profile) {
        JWSHeader header = new JWSHeader(JWSAlgorithm.HS512);

        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(VALID_DURATION);

        JWTClaimsSet.Builder jwtClaimsSet = new JWTClaimsSet.Builder()
                .issuer("bkument.vn.edu.hcmut")
                .subject(account.getId())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(expiresAt))
                .jwtID(UUID.randomUUID().toString());

        // ---- custom claims ----
        jwtClaimsSet.claim("username", account.getUsername());
        jwtClaimsSet.claim("scope", buildScope(account));

        if (profile != null) {
            jwtClaimsSet.claim("profile_id", profile.getId());
            jwtClaimsSet.claim("email", profile.getEmail());
            jwtClaimsSet.claim("university_id", profile.getUniversityId());
            String fullName = (profile.getLastName() != null ? profile.getLastName() : "") + " " +
                    (profile.getFirstName() != null ? profile.getFirstName() : "");
            jwtClaimsSet.claim("name", fullName.trim());
        }

        JWSObject jwsObject = new JWSObject(header, new Payload(jwtClaimsSet.build().toJSONObject()));

        try {
            jwsObject.sign(new MACSigner(SIGNER_KEY.getBytes()));
            return new TokenInfo(jwsObject.serialize(), Date.from(expiresAt));
        } catch (JOSEException e) {
            log.error("Failed to sign JWT token", e);
            throw new RuntimeException("Error while creating token", e);
        }
    }

    /**
     * Verifies a JWT’s signature, expiration, and invalidation state.
     */
    private SignedJWT verifyToken(String token, boolean isRefresh)
            throws JOSEException, ParseException {

        JWSVerifier verifier = new MACVerifier(SIGNER_KEY.getBytes());
        SignedJWT signedJWT = SignedJWT.parse(token);

        // Determine correct expiration time based on context (Authentication or Refreshing)
        Date expiryTime = (isRefresh)
                ? new Date(signedJWT
                        .getJWTClaimsSet()
                        .getIssueTime()
                        .toInstant()
                        .plus(REFRESHABLE_DURATION, ChronoUnit.SECONDS)
                        .toEpochMilli())
                : signedJWT.getJWTClaimsSet().getExpirationTime();

        boolean verified = signedJWT.verify(verifier);

        // Reject if signature invalid or token has passed its allowed time window
        if (!(verified && expiryTime.after(new Date()))) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        // Reject if the token was previously invalidated
        if (invalidatedTokenRepository.existsById(signedJWT.getJWTClaimsSet().getJWTID())) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        return signedJWT;
    }

    /**
     * Recovers profile data from an old token if the Profile Service is unreachable.
     * Prevents the new token from losing critical claims.
     */
    private ProfileResponse extractProfileFromOldToken(SignedJWT oldToken) {
        String fullName = parseStringClaim(oldToken, "name");
        String lastName = "";
        String firstName = "";

        if (fullName != null && !fullName.trim().isEmpty()) {
            String[] parts = fullName.split(" ", 2);
            lastName = parts.length > 0 ? parts[0] : "";
            firstName = parts.length > 1 ? parts[1] : "";
        }

        return ProfileResponse.builder()
                .id(parseStringClaim(oldToken, "profile_id"))
                .email(parseStringClaim(oldToken, "email"))
                .universityId(parseIntegerClaim(oldToken, "university_id"))
                .lastName(lastName)
                .firstName(firstName)
                .build();
    }

    private String buildScope(Account account) {
        StringJoiner stringJoiner = new StringJoiner(" ");
        if (!CollectionUtils.isEmpty(account.getRoles())) {
            account.getRoles().forEach(role -> stringJoiner.add(role.name()));
        }
        return stringJoiner.toString();
    }

    private String parseStringClaim(SignedJWT jwt, String keyword) {
        try {
            return jwt.getJWTClaimsSet().getStringClaim(keyword);
        } catch (ParseException e) {
            return null;
        }
    }

    private Integer parseIntegerClaim(SignedJWT jwt, String keyword) {
        try {
            return jwt.getJWTClaimsSet().getIntegerClaim(keyword);
        } catch (ParseException e) {
            log.warn("Failed to parse integer claim for key: {}", keyword);
            return null;
        }
    }

    private record TokenInfo(String token, Date expiryDate) {}
}
