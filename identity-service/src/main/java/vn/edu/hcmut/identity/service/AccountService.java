package vn.edu.hcmut.identity.service;

import java.util.HashSet;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import vn.edu.hcmut.event.dto.NotificationEvent;
import vn.edu.hcmut.identity.constant.UserRole;
import vn.edu.hcmut.identity.dto.request.AccountUpdateRequest;
import vn.edu.hcmut.identity.dto.request.UserCreationRequest;
import vn.edu.hcmut.identity.dto.response.AccountResponse;
import vn.edu.hcmut.identity.dto.response.PageResponse;
import vn.edu.hcmut.identity.entity.Account;
import vn.edu.hcmut.identity.exception.AppException;
import vn.edu.hcmut.identity.exception.ErrorCode;
import vn.edu.hcmut.identity.mapper.AccountMapper;
import vn.edu.hcmut.identity.mapper.ProfileMapper;
import vn.edu.hcmut.identity.repository.AccountRepository;
import vn.edu.hcmut.identity.repository.httpclient.LmsClient;
import vn.edu.hcmut.identity.repository.httpclient.ProfileClient;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class AccountService {
    AccountRepository accountRepository;

    AccountMapper accountMapper;
    ProfileMapper profileMapper;
    PasswordEncoder passwordEncoder;

    ProfileClient profileClient;
    LmsClient lmsClient;

    KafkaTemplate<String, Object> kafkaTemplate;

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

        // initialize roles
        HashSet<UserRole> roles = new HashSet<>();
        roles.add(request.getAccount().getRole());
        account.setRoles(roles);

        account = accountRepository.save(account);

        // Synchronous cross-service call to create a profile
        var profile = profileMapper.toProfileCreationRequest(request);
        profile.setAccountId(account.getId());
        profileClient.createProfile(profile);

        // Asynchronous event publishing for notifications
        NotificationEvent noti = NotificationEvent.builder()
                .channel("EMAIL")
                .recipient(request.getEmail())
                .subject("Welcome to BKUMENT")
                .body("Hello, " + request.getAccount().getUsername())
                .build();

        // Publish message to kafka
        kafkaTemplate.send("notification-delivery", noti);

        return accountMapper.toAccountResponse(account);
    }

    @Transactional
    @PostAuthorize("#accountId == authentication.name or hasRole('ADMIN')")
    public AccountResponse updateAccount(String accountId, AccountUpdateRequest request) {
        Account account = accountRepository
                .findById(accountId)
                .orElseThrow(() -> new AppException(ErrorCode.ACCOUNT_NOT_FOUND));

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

        List<AccountResponse> accountResponses = accounts.getContent()
                .stream()
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

    @PostAuthorize("returnObject.id == authentication.name or hasRole('ADMIN')")
    public AccountResponse getAccount(String accountId) {
        Account account = accountRepository
                .findById(accountId)
                .orElseThrow(() -> new AppException(ErrorCode.ACCOUNT_NOT_FOUND));
        return accountMapper.toAccountResponse(account);
    }

    /**
     * Deletes an account and orchestrates the deletion of related external data.
     * Consider refactoring this to use Kafka events (Saga Choreography pattern) in the future.
     */
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public void deleteAccount(String accountId) {
        if (!accountRepository.existsById(accountId)) {
            throw new AppException(ErrorCode.ACCOUNT_NOT_FOUND);
        }

        String profileId = null;
        try {
            var profile = profileClient.getProfileByAccountId(accountId).getResult();
            if (profile != null) profileId = profile.getId();
        } catch (Exception e) {
            log.warn("Cannot fetch profile for account {}", accountId, e);
        }

        if (profileId != null) {
            try {
                lmsClient.deleteTutor(profileId);
            } catch (Exception e) {
                log.error("Error deleting tutor data for profile with id: {}", profileId, e);
                throw new AppException(ErrorCode.DELETE_LMS_FAILED);
            }

            try {
                profileClient.deleteProfile(profileId);
            } catch (Exception e) {
                log.error("Error deleting Neo4j data for profile: {}", profileId, e);
                throw new AppException(ErrorCode.DELETE_PROFILE_FAILED);
            }
        }

        accountRepository.deleteById(accountId);
        log.info("Account {} and related data have been deleted", accountId);
    }

    @Transactional
    public void addRoleToUser(String accountId, String roleName) {
        Account account = accountRepository
                .findById(accountId)
                .orElseThrow(() -> new AppException(ErrorCode.ACCOUNT_NOT_FOUND));
        try {
            UserRole role = UserRole.valueOf(roleName.toUpperCase());

            if (account.getRoles() == null) account.setRoles(new HashSet<>());
            account.getRoles().add(role);
            accountRepository.save(account);
            log.info("Successfully added role {} to account {}", roleName, accountId);
        } catch (IllegalArgumentException e) {
            throw new AppException(ErrorCode.INVALID_ROLE);
        }
    }
}
