package vn.edu.hcmut.identity.service;

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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import vn.edu.hcmut.identity.dto.request.AuthenticationRequest;
import vn.edu.hcmut.identity.dto.request.IntrospectRequest;
import vn.edu.hcmut.identity.dto.request.LogoutRequest;
import vn.edu.hcmut.identity.dto.request.RefreshRequest;
import vn.edu.hcmut.identity.dto.response.AuthenticationResponse;
import vn.edu.hcmut.identity.dto.response.IntrospectResponse;
import vn.edu.hcmut.identity.entity.Account;
import vn.edu.hcmut.identity.entity.InvalidatedToken;
import vn.edu.hcmut.identity.exception.AppException;
import vn.edu.hcmut.identity.exception.ErrorCode;
import vn.edu.hcmut.identity.repository.AccountRepository;
import vn.edu.hcmut.identity.repository.InvalidatedTokenRepository;

import java.text.ParseException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AuthenticationService {
    AccountRepository accountRepository;
    InvalidatedTokenRepository invalidatedTokenRepository;

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
     * Verifies whether a given token is valid.
     *
     * @param request contains the token to introspect
     * @return {@link IntrospectResponse} indicating whether the token is valid
     */
    public IntrospectResponse introspect(IntrospectRequest request)
            throws JOSEException, ParseException {
        var token = request.getToken();
        boolean isValid = true;

        try {
            verifyToken(token, false);
        } catch (AppException e) {
            isValid = false;
        }

        return IntrospectResponse.builder()
                .valid(isValid)
                .build();
    }

    /**
     * Authenticates the user by username and password, then issues a JWT.
     *
     * @param request contains username and password credentials
     * @return {@link AuthenticationResponse} containing a signed JWT if successful
     * @throws AppException if credentials are invalid or user not found
     */
    public AuthenticationResponse authenticate(AuthenticationRequest request) {
        PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(10);
        var account = accountRepository
                .findByUsername(request.getUsername())
                .orElseThrow(() -> new AppException(ErrorCode.ACCOUNT_NOT_EXISTED));

        boolean authenticated = passwordEncoder.matches(request.getPassword(), account.getPassword());

        if (!authenticated) throw new AppException(ErrorCode.UNAUTHENTICATED);

        var token = generateToken(account);

        return AuthenticationResponse.builder()
                .token(token.token)
                .authenticated(true)
                .expiryTime(token.expiryDate)
                .build();
    }

    /**
     * Invalidates a token by storing its ID (JTI) into the invalidation repository.
     * @param request contains the token to invalidate
     */
    public void logout(LogoutRequest request) throws ParseException, JOSEException {
        try {
            var signToken = verifyToken(request.getToken(), true);

            String jti = signToken.getJWTClaimsSet().getJWTID();
            Date expiryTime = signToken.getJWTClaimsSet().getExpirationTime();

            InvalidatedToken invalidatedToken = InvalidatedToken.builder()
                    .id(jti)
                    .expiryTime(expiryTime)
                    .build();

            invalidatedTokenRepository.save(invalidatedToken);
        } catch (AppException exception){
            log.info("Token already expired");
        }
    }

    public AuthenticationResponse refreshToken(RefreshRequest request) throws ParseException, JOSEException {
        var signedJWT = verifyToken(request.getToken(), true);

        // Invalidate old token
        var jti = signedJWT.getJWTClaimsSet().getJWTID();
        var expiryTime = signedJWT.getJWTClaimsSet().getExpirationTime();

        invalidatedTokenRepository.save(
                InvalidatedToken.builder().id(jti).expiryTime(expiryTime).build()
        );

        // Reissue new token
        var accountId = signedJWT.getJWTClaimsSet().getSubject();
        var account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));

        var token = generateToken(account);

        return AuthenticationResponse.builder()
                .token(token.token)
                .expiryTime(token.expiryDate)
                .build();
    }


    /* Helper method */
    private TokenInfo generateToken(Account account) {
        JWSHeader header = new JWSHeader(JWSAlgorithm.HS512);

        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(VALID_DURATION);
        String jti = UUID.randomUUID().toString();

        JWTClaimsSet.Builder jwtClaimsSet = new JWTClaimsSet.Builder()
                .issuer("bkument.vn.edu.hcmut")
                .subject(account.getId())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(expiresAt))
                .jwtID(jti);

        // ---- Custom auth claims
        jwtClaimsSet.claim("username", account.getUsername());
        jwtClaimsSet.claim("scope", account.getRole().name());

        JWSObject jwsObject = new JWSObject(header, new Payload(jwtClaimsSet.build().toJSONObject()));

        try {
            jwsObject.sign(new MACSigner(SIGNER_KEY.getBytes()));
            String token = jwsObject.serialize();
            Date expiryDate = Date.from(expiresAt);
            return new TokenInfo(token, expiryDate);
        } catch (JOSEException e) {
            log.error("Cannot create token", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Verifies a JWT’s signature, expiration, and invalidation state.
     *
     * @param token     the JWT string to verify
     * @param isRefresh whether this is for token refresh (uses refreshable duration)
     * @return parsed and verified {@link SignedJWT}
     * @throws AppException if token is invalid, expired, or revoked
     */
    private SignedJWT verifyToken(String token, boolean isRefresh) throws JOSEException, ParseException {

        JWSVerifier verifier = new MACVerifier(SIGNER_KEY.getBytes());
        SignedJWT signedJWT = SignedJWT.parse(token);

        // Determine correct expiration time depending on context
        Date expiryTime = (isRefresh)
                ? new Date(signedJWT.getJWTClaimsSet().getIssueTime()
                .toInstant().plus(REFRESHABLE_DURATION, ChronoUnit.SECONDS).toEpochMilli())
                : signedJWT.getJWTClaimsSet().getExpirationTime();

        boolean verified = signedJWT.verify(verifier);

        // Reject if signature invalid or token expired
        if (!(verified && expiryTime.after(new Date())))
            throw new AppException(ErrorCode.UNAUTHENTICATED);

        // Reject if the token was previously invalidated (e.g., logged out)
        if (invalidatedTokenRepository.existsById(signedJWT.getJWTClaimsSet().getJWTID()))
            throw new AppException(ErrorCode.UNAUTHENTICATED);

        return signedJWT;
    }

    private record TokenInfo(String token, Date expiryDate) {}
}
