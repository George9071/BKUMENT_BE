package vn.edu.hcmut.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import vn.edu.hcmut.identity.constant.UserRole;
import vn.edu.hcmut.identity.dto.request.AuthenticationRequest;
import vn.edu.hcmut.identity.dto.request.IntrospectRequest;
import vn.edu.hcmut.identity.dto.request.LogoutRequest;
import vn.edu.hcmut.identity.dto.request.RefreshRequest;
import vn.edu.hcmut.identity.dto.response.APIResponse;
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

@ExtendWith(MockitoExtension.class)
public class AuthenticationServiceTest {

    @Mock
    AccountRepository accountRepository;

    @Mock
    InvalidatedTokenRepository invalidatedTokenRepository;

    @Mock
    ProfileClient profileClient;

    @Mock
    PasswordEncoder passwordEncoder;

    @InjectMocks
    AuthenticationService authenticationService;

    private static final String TEST_SIGNER_KEY =
            "Z6+cjoOr6OUEh1wSuIL8ZVOBu0zEDYCUn8/BrI0/mddOOzyYqAy3xAYFnAlMSrjqYsr839BJ+p4xv7HOmaHIXQ==";
    private static final long VALID_DURATION = 3600L;
    private static final long REFRESHABLE_DURATION = 86400L;

    private Account mockAccount;
    private ProfileResponse mockProfile;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authenticationService, "SIGNER_KEY", TEST_SIGNER_KEY);
        ReflectionTestUtils.setField(authenticationService, "VALID_DURATION", VALID_DURATION);
        ReflectionTestUtils.setField(authenticationService, "REFRESHABLE_DURATION", REFRESHABLE_DURATION);

        mockAccount = Account.builder()
                .id("acc-001")
                .username("testuser")
                .password("encoded_password")
                .roles(new HashSet<>(Set.of(UserRole.USER)))
                .build();

        mockProfile = ProfileResponse.builder()
                .id("prof-001")
                .email("test@hcmut.edu.vn")
                .firstName("An")
                .lastName("Nguyen")
                .universityId(2100000)
                .build();
    }

    @Nested
    @DisplayName("authenticate()")
    class Authenticate {

        @Test
        @DisplayName("Happy path — returns token for valid credentials")
        void authenticate_success() {
            when(accountRepository.findByUsername("testuser")).thenReturn(Optional.of(mockAccount));
            when(passwordEncoder.matches("raw_password", "encoded_password")).thenReturn(true);
            when(profileClient.getProfileByAccountId("acc-001"))
                    .thenReturn(APIResponse.<ProfileResponse>builder()
                            .result(mockProfile)
                            .build());

            AuthenticationResponse response =
                    authenticationService.authenticate(new AuthenticationRequest("testuser", "raw_password"));

            assertThat(response.isAuthenticated()).isTrue();
            assertThat(response.getToken()).isNotBlank();
            assertThat(response.getExpiryTime()).isAfter(new Date());
        }

        @Test
        @DisplayName("Throws ACCOUNT_NOT_FOUND for unknown username")
        void authenticate_unknownUsername_throwsException() {
            when(accountRepository.findByUsername("unknown")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authenticationService.authenticate(new AuthenticationRequest("unknown", "any")))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.ACCOUNT_NOT_FOUND);
        }

        @Test
        @DisplayName("Throws UNAUTHENTICATED for wrong password")
        void authenticate_wrongPassword_throwsException() {
            when(accountRepository.findByUsername("testuser")).thenReturn(Optional.of(mockAccount));
            when(passwordEncoder.matches("wrong", "encoded_password")).thenReturn(false);

            assertThatThrownBy(() -> authenticationService.authenticate(new AuthenticationRequest("testuser", "wrong")))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.UNAUTHENTICATED);
        }

        @Test
        @DisplayName("ADMIN skips profile fetch — token is issued without profile claims")
        void authenticate_adminSkipsProfileFetch() {
            Account adminAccount = Account.builder()
                    .id("admin-001")
                    .username("admin")
                    .password("encoded")
                    .roles(new HashSet<>(Set.of(UserRole.ADMIN)))
                    .build();

            when(accountRepository.findByUsername("admin")).thenReturn(Optional.of(adminAccount));
            when(passwordEncoder.matches("admin_pass", "encoded")).thenReturn(true);

            AuthenticationResponse response =
                    authenticationService.authenticate(new AuthenticationRequest("admin", "admin_pass"));

            assertThat(response.getToken()).isNotBlank();
            // profileClient must never be called for ADMIN
            verify(profileClient, never()).getProfileByAccountId(any());
        }

        @Test
        @DisplayName("Throws PROFILE_NOT_FOUND when profile fetch fails for non-admin")
        void authenticate_profileFetchFails_throwsException() {
            when(accountRepository.findByUsername("testuser")).thenReturn(Optional.of(mockAccount));
            when(passwordEncoder.matches(any(), any())).thenReturn(true);
            when(profileClient.getProfileByAccountId("acc-001"))
                    .thenThrow(new RuntimeException("Profile Service down"));

            assertThatThrownBy(() ->
                            authenticationService.authenticate(new AuthenticationRequest("testuser", "raw_password")))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.PROFILE_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("introspect()")
    class Introspect {

        @Test
        @DisplayName("Returns valid=true and profileId for a valid token")
        void introspect_validToken_returnsValidAndProfileId() throws Exception {
            String token = buildToken("acc-001", "prof-001", VALID_DURATION);
            when(invalidatedTokenRepository.existsById(any())).thenReturn(false);

            IntrospectResponse response = authenticationService.introspect(new IntrospectRequest(token));

            assertThat(response.isValid()).isTrue();
            assertThat(response.getProfileId()).isEqualTo("prof-001");
        }

        @Test
        @DisplayName("Returns valid=false for an expired token")
        void introspect_expiredToken_returnsInvalid() throws Exception {
            String token = buildToken("acc-001", "prof-001", -3600L); // expired 1h ago

            IntrospectResponse response = authenticationService.introspect(new IntrospectRequest(token));

            assertThat(response.isValid()).isFalse();
            assertThat(response.getProfileId()).isNull();
        }

        @Test
        @DisplayName("Returns valid=false for an invalidated (logged-out) token")
        void introspect_invalidatedToken_returnsInvalid() throws Exception {
            String token = buildToken("acc-001", "prof-001", VALID_DURATION);
            when(invalidatedTokenRepository.existsById(any())).thenReturn(true);

            IntrospectResponse response = authenticationService.introspect(new IntrospectRequest(token));

            assertThat(response.isValid()).isFalse();
        }

        @Test
        @DisplayName("Returns valid=false for a malformed token string")
        void introspect_malformedToken_returnsInvalid() {
            IntrospectResponse response = authenticationService.introspect(new IntrospectRequest("this.is.not.a.jwt"));

            assertThat(response.isValid()).isFalse();
        }
    }

    @Nested
    @DisplayName("logout()")
    class Logout {

        @Test
        @DisplayName("Happy path — stores JTI to invalidate token")
        void logout_validToken_savesInvalidatedToken() throws Exception {
            String token = buildToken("acc-001", "prof-001", VALID_DURATION);
            when(invalidatedTokenRepository.existsById(any())).thenReturn(false);

            authenticationService.logout(new LogoutRequest(token));

            verify(invalidatedTokenRepository).save(any(InvalidatedToken.class));
        }

        @Test
        @DisplayName("Silently ignores already expired tokens")
        void logout_expiredToken_noException() throws Exception {
            String expiredToken = buildTokenWithIssueTime(
                    "acc-001", "prof-001", Instant.now().minus(25, ChronoUnit.HOURS), VALID_DURATION);

            authenticationService.logout(new LogoutRequest(expiredToken));

            verify(invalidatedTokenRepository, never()).save(any());
        }

        @Test
        @DisplayName("Silently ignores already invalidated tokens")
        void logout_alreadyInvalidatedToken_noException() throws Exception {
            String token = buildToken("acc-001", "prof-001", VALID_DURATION);
            when(invalidatedTokenRepository.existsById(any())).thenReturn(true);

            authenticationService.logout(new LogoutRequest(token));

            // Token already invalidated — second invalidation is silently skipped
            verify(invalidatedTokenRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("refreshToken()")
    class RefreshToken {

        @Test
        @DisplayName("Happy path — issues new token and invalidates old one")
        void refreshToken_success() throws Exception {
            String oldToken = buildToken("acc-001", "prof-001", VALID_DURATION);
            when(invalidatedTokenRepository.existsById(any())).thenReturn(false);
            when(accountRepository.findById("acc-001")).thenReturn(Optional.of(mockAccount));
            when(profileClient.getProfileByAccountId("acc-001"))
                    .thenReturn(APIResponse.<ProfileResponse>builder()
                            .result(mockProfile)
                            .build());

            AuthenticationResponse response = authenticationService.refreshToken(new RefreshRequest(oldToken));

            // Old token must be invalidated
            verify(invalidatedTokenRepository).save(any(InvalidatedToken.class));
            // New token must be issued
            assertThat(response.getToken()).isNotBlank();
            assertThat(response.getToken()).isNotEqualTo(oldToken);
        }

        @Test
        @DisplayName("Falls back to old token claims when Profile Service is down")
        void refreshToken_profileServiceDown_usesOldTokenClaims() throws Exception {
            String oldToken = buildToken("acc-001", "prof-001", VALID_DURATION);
            when(invalidatedTokenRepository.existsById(any())).thenReturn(false);
            when(accountRepository.findById("acc-001")).thenReturn(Optional.of(mockAccount));
            when(profileClient.getProfileByAccountId("acc-001"))
                    .thenThrow(new RuntimeException("Profile Service down"));

            // Should NOT throw — falls back gracefully
            AuthenticationResponse response = authenticationService.refreshToken(new RefreshRequest(oldToken));

            assertThat(response.getToken()).isNotBlank();
        }

        @Test
        @DisplayName("Throws UNAUTHENTICATED for an already invalidated token")
        void refreshToken_invalidatedToken_throwsException() throws Exception {
            String token = buildToken("acc-001", "prof-001", VALID_DURATION);
            when(invalidatedTokenRepository.existsById(any())).thenReturn(true);

            assertThatThrownBy(() -> authenticationService.refreshToken(new RefreshRequest(token)))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.UNAUTHENTICATED);
        }

        @Test
        @DisplayName("Throws UNAUTHENTICATED when token is outside refreshable window")
        void refreshToken_outsideRefreshWindow_throwsException() throws Exception {
            // Token issued more than REFRESHABLE_DURATION ago
            String expiredRefreshToken = buildTokenWithIssueTime(
                    "acc-001", "prof-001", Instant.now().minus(REFRESHABLE_DURATION + 3600, ChronoUnit.SECONDS));

            assertThatThrownBy(() -> authenticationService.refreshToken(new RefreshRequest(expiredRefreshToken)))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.UNAUTHENTICATED);
        }
    }

    private String buildToken(String accountId, String profileId, long durationSeconds) throws JOSEException {
        return buildTokenWithIssueTime(accountId, profileId, Instant.now(), durationSeconds);
    }

    private String buildTokenWithIssueTime(String accountId, String profileId, Instant issueTime) throws JOSEException {
        // For testing outside-refresh-window: issue time in the past, expiry also in the past
        return buildTokenWithIssueTime(accountId, profileId, issueTime, VALID_DURATION);
    }

    private String buildTokenWithIssueTime(String accountId, String profileId, Instant issueTime, long durationSeconds)
            throws JOSEException {
        JWSHeader header = new JWSHeader(JWSAlgorithm.HS512);

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(accountId)
                .issuer("bkument.vn.edu.hcmut")
                .issueTime(Date.from(issueTime))
                .expirationTime(Date.from(issueTime.plusSeconds(durationSeconds)))
                .jwtID(UUID.randomUUID().toString())
                .claim("profile_id", profileId)
                .claim("scope", "STUDENT")
                .build();

        SignedJWT signedJWT = new SignedJWT(header, claims);
        JWSSigner signer = new MACSigner(TEST_SIGNER_KEY.getBytes());
        signedJWT.sign(signer);

        return signedJWT.serialize();
    }
}
