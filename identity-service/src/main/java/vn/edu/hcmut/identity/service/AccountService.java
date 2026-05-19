package vn.edu.hcmut.identity.service;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;

import feign.FeignException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import vn.edu.hcmut.event.AccountDeletedEvent;
import vn.edu.hcmut.event.EmailSendAfterCommitEvent;
import vn.edu.hcmut.event.EmailSendEvent;
import vn.edu.hcmut.identity.constant.UserRole;
import vn.edu.hcmut.identity.dto.request.AccountCreationRequest;
import vn.edu.hcmut.identity.dto.request.AccountUpdateRequest;
import vn.edu.hcmut.identity.dto.request.UserCreationRequest;
import vn.edu.hcmut.identity.dto.response.AccountResponse;
import vn.edu.hcmut.identity.dto.response.PageResponse;
import vn.edu.hcmut.identity.dto.response.ProfileResponse;
import vn.edu.hcmut.identity.entity.Account;
import vn.edu.hcmut.identity.entity.VerificationToken;
import vn.edu.hcmut.identity.exception.AppException;
import vn.edu.hcmut.identity.exception.ErrorCode;
import vn.edu.hcmut.identity.mapper.AccountMapper;
import vn.edu.hcmut.identity.mapper.ProfileMapper;
import vn.edu.hcmut.identity.repository.AccountRepository;
import vn.edu.hcmut.identity.repository.VerificationTokenRepository;
import vn.edu.hcmut.identity.repository.httpclient.ProfileClient;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class AccountService {

    AccountRepository accountRepository;
    VerificationTokenRepository tokenRepository;

    AccountMapper accountMapper;
    ProfileMapper profileMapper;
    PasswordEncoder passwordEncoder;

    ProfileClient profileClient;

    VerificationTokenService verificationTokenService;

    KafkaTemplate<String, Object> kafkaTemplate;

    ApplicationEventPublisher eventPublisher;

    /**
     * Creates a full user ecosystem (Identity Account + External Profile) and triggers a notification through Kafka.
     */
    @Transactional
    public AccountResponse createUser(UserCreationRequest request) {
        if (accountRepository.existsByUsername(request.getAccount().getUsername())) {
            throw new AppException(ErrorCode.ACCOUNT_ALREADY_EXISTS);
        }

        Account account = accountMapper.toAccount(request.getAccount());
        account.setPassword(passwordEncoder.encode(request.getAccount().getPassword()));

        HashSet<UserRole> roles = new HashSet<>();
        roles.add(request.getAccount().getRole());
        account.setRoles(roles);

        account = accountRepository.save(account);

        // Synchronous cross-service call to create a profile
        var profile = profileMapper.toProfileCreationRequest(request);
        profile.setAccountId(account.getId());

        try {
            profileClient.createProfile(profile);
        } catch (Exception ex) {
            log.error("Profile creation failed; rolling back account {}", account.getId(), ex);
            throw new AppException(ErrorCode.SYNC_FAILED);
        }

        String verifyLink = verificationTokenService.createEmailVerificationLink(account.getId());

        eventPublisher.publishEvent(new EmailSendAfterCommitEvent(
                "email-delivery",
                EmailSendEvent.builder()
                        .recipient(request.getEmail())
                        .subject("Xác minh tài khoản BKUMENT")
                        .body(buildVerifyEmailBody(request.getAccount().getUsername(), verifyLink))
                        .build()));

        return accountMapper.toAccountResponse(account);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public AccountResponse createModeratorAccount(AccountCreationRequest request) {
        if (accountRepository.existsByUsername(request.getUsername())) {
            throw new AppException(ErrorCode.ACCOUNT_ALREADY_EXISTS);
        }

        Account account = accountMapper.toAccount(request);
        account.setPassword(passwordEncoder.encode(request.getPassword()));

        HashSet<UserRole> roles = new HashSet<>();
        roles.add(UserRole.MODERATOR);
        account.setRoles(roles);

        account = accountRepository.save(account);

        log.info("Moderator account created successfully: {}", account.getUsername());

        return accountMapper.toAccountResponse(account);
    }

    @Transactional
    @PreAuthorize("#accountId == authentication.name or hasRole('ADMIN')")
    public AccountResponse updateAccount(String accountId, AccountUpdateRequest request) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AppException(ErrorCode.ACCOUNT_NOT_FOUND));

        if (StringUtils.hasText(request.getUsername())
                && !account.getUsername().equals(request.getUsername())) {

            if (accountRepository.existsByUsername(request.getUsername())) {
                throw new AppException(ErrorCode.USERNAME_ALREADY_EXISTS);
            }
        }

        accountMapper.updateAccount(account, request);

        if (request.getPassword() != null && !request.getPassword().isEmpty()) {
            account.setPassword(passwordEncoder.encode(request.getPassword()));
        }

        return accountMapper.toAccountResponse(accountRepository.save(account));
    }

    @PreAuthorize("hasRole('ADMIN')")
    public PageResponse<AccountResponse> getAccounts(int page, int size) {
        Pageable pageable = PageRequest.of((page > 0) ? page - 1 : 0, size);
        Page<Account> accounts = accountRepository.findAll(pageable);

        List<AccountResponse> accountResponses = accounts.getContent().stream()
                .map(accountMapper::toAccountResponse)
                .toList();

        return PageResponse.<AccountResponse>builder()
                .currentPage(page)
                .totalPages(accounts.getTotalPages())
                .pageSize(accounts.getSize())
                .totalElements(accounts.getTotalElements())
                .data(accountResponses)
                .build();
    }

    @PreAuthorize("hasRole('ADMIN')")
    public PageResponse<AccountResponse> getModerators(int page, int size) {
        Pageable pageable = PageRequest.of((page > 0) ? page - 1 : 0, size);
        Page<Account> accounts = accountRepository.findAllByRolesContaining(UserRole.MODERATOR, pageable);

        List<AccountResponse> responses = accounts.getContent().stream()
                .map(accountMapper::toAccountResponse)
                .toList();

        return PageResponse.<AccountResponse>builder()
                .currentPage(page)
                .totalPages(accounts.getTotalPages())
                .pageSize(accounts.getSize())
                .totalElements(accounts.getTotalElements())
                .data(responses)
                .build();
    }


    @PostAuthorize("returnObject.id == authentication.name or hasRole('ADMIN')")
    public AccountResponse getAccount(String accountId) {
        Account account =
                accountRepository.findById(accountId).orElseThrow(() -> new AppException(ErrorCode.ACCOUNT_NOT_FOUND));
        return accountMapper.toAccountResponse(account);
    }

    /**
     * Deletes an account and orchestrates the deletion of related external data.
     * Consider refactoring this to use Kafka events (Saga Choreography pattern) in the future.
     */
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public void deleteAccount(String accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AppException(ErrorCode.ACCOUNT_NOT_FOUND));

        String profileId = null;
        try {
            var profile = profileClient.getProfileByAccountId(accountId);
            if (profile != null) profileId = profile.getId();
        } catch (Exception e) {
            log.warn("Cannot fetch profile for account {} — downstream cleanup may be incomplete",
                    accountId, e);
        }

        accountRepository.delete(account);

        eventPublisher.publishEvent(AccountDeletedEvent.builder()
                .accountId(accountId)
                .profileId(profileId)
                .build());

        log.info("Account {} deleted; Account deleted event published (profileId={})", accountId, profileId);
    }

    @Transactional
    public void addRoleToUser(String accountId, UserRole role) {
        Account account =
                accountRepository.findById(accountId).orElseThrow(() -> new AppException(ErrorCode.ACCOUNT_NOT_FOUND));

        if (account.getRoles() == null) account.setRoles(new HashSet<>());
        account.getRoles().add(role);
        accountRepository.save(account);
        log.info("Successfully added role {} to account {}", role.name(), accountId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void removeRole(String accountId, UserRole role) {
        if (role == UserRole.USER) {
            // Prevent removing the base role
            throw new AppException(ErrorCode.INVALID_ROLE);
        }

        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AppException(ErrorCode.ACCOUNT_NOT_FOUND));

        if (account.getRoles() != null && account.getRoles().remove(role)) {
            accountRepository.save(account);
            log.info("Removed role {} from account {}", role.name(), accountId);
        } else {
            log.info("Account {} did not have role {}, skipping", accountId, role.name());
        }
    }

    @Transactional
    public void verifyEmail(String token) {
        VerificationToken verifyToken =
                verificationTokenService.validateToken(token, VerificationToken.TokenType.EMAIL_VERIFICATION);

        verificationTokenService.markAsUsed(verifyToken);

        try {
            profileClient.verifyEmail(verifyToken.getAccountId());
        } catch (Exception e) {
            log.error("Failed to update emailVerified for account {}", verifyToken.getAccountId(), e);
            throw new AppException(ErrorCode.SYNC_FAILED);
        }
    }

    @Async("forgotPasswordExecutor")
    public void forgotPassword(String email) {
        ProfileResponse profile;
        try {
            profile = profileClient.getProfileByEmail(email);
        } catch (FeignException.NotFound e) {
            log.info("Forgot password requested for non-existent email: {}", email);
            return;
        } catch (Exception e) {
            log.error("CRITICAL: Profile-service connection failed during forgot-password for email: {}", email, e);
            return;
        }

        if (profile == null) return;

        try {
            String otp = verificationTokenService.createPasswordResetOtp(profile.getAccountId());

            kafkaTemplate.send(
                    "email-delivery",
                    EmailSendEvent.builder()
                            .recipient(email)
                            .subject("Mã đặt lại mật khẩu BKUMENT")
                            .body(buildResetPasswordBody(otp))
                            .build());

            log.info("Successfully generated OTP and published email event to Kafka for email: {}", email);
        } catch (Exception e) {
            log.error("CRITICAL: Failed to process OTP or send Kafka event for password reset. Email: {}", email, e);
            throw e;
        }
    }

    @Transactional
    public void resetPassword(String email, String otp, String newPassword) {
        var profile = profileClient.getProfileByEmail(email);
        if (profile == null) throw new AppException(ErrorCode.ACCOUNT_NOT_FOUND);

        VerificationToken vToken = tokenRepository
                .findByTokenAndAccountIdAndTypeAndUsedFalse(
                        otp, profile.getAccountId(), VerificationToken.TokenType.PASSWORD_RESET)
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_VERIFICATION_TOKEN));

        if (vToken.getExpiresAt().isBefore(Instant.now())) {
            throw new AppException(ErrorCode.VERIFICATION_TOKEN_EXPIRED);
        }

        Account account = accountRepository
                .findById(profile.getAccountId())
                .orElseThrow(() -> new AppException(ErrorCode.ACCOUNT_NOT_FOUND));

        account.setPassword(passwordEncoder.encode(newPassword));
        accountRepository.save(account);

        verificationTokenService.markAsUsed(vToken);
        log.info("Password reset successfully for account {}", account.getId());
    }

    private String buildVerifyEmailBody(String username, String verifyLink) {
        return """
		<h2>Chào %s,</h2>
		<p>Cảm ơn bạn đã đăng ký tài khoản BKUMENT.</p>
		<p>Vui lòng click vào link bên dưới để xác minh email:</p>
		<a href="%s" style="padding:10px 20px;background:#007bff;color:white;
			border-radius:5px;text-decoration:none;">Xác minh email</a>
		<p>Link có hiệu lực trong 24 giờ.</p>
		<p>Nếu bạn không đăng ký tài khoản này, vui lòng bỏ qua email này.</p>
		"""
                .formatted(username, verifyLink);
    }

    private String buildResetPasswordBody(String otp) {
        return """
<div style="font-family: Arial, sans-serif; line-height: 1.6; color: #333;">
	<h2>Đặt lại mật khẩu</h2>
	<p>Chúng tôi nhận được yêu cầu đặt lại mật khẩu cho tài khoản của bạn.</p>
	<p>Đây là mã xác nhận (OTP) của bạn. Vui lòng nhập mã này vào trang đổi mật khẩu:</p>

	<div style="background-color: #f8f9fa; border: 1px dashed #ccc; border-radius: 8px; padding: 15px; text-align: center; margin: 20px 0; max-width: 300px;">
		<span style="font-size: 32px; font-weight: bold; letter-spacing: 8px; color: #dc3545;">%s</span>
	</div>

	<p>Mã này có hiệu lực trong 1 giờ.</p>
	<p>Nếu bạn không yêu cầu, vui lòng bỏ qua email này để bảo vệ tài khoản.</p>
</div>
"""
                .formatted(otp);
    }
}
