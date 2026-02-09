package vn.edu.hcmut.identity.service;

import java.util.HashSet;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import vn.edu.hcmut.identity.constant.UserRole;
import vn.edu.hcmut.identity.dto.request.AccountCreationRequest;
import vn.edu.hcmut.identity.dto.request.AccountUpdateRequest;
import vn.edu.hcmut.identity.dto.request.UserCreationRequest;
import vn.edu.hcmut.identity.dto.response.AccountResponse;
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
    PasswordEncoder passwordEncoder;
    ProfileClient profileClient;
    LmsClient lmsClient;
    ProfileMapper profileMapper;

    @Transactional
    public AccountResponse createUser(UserCreationRequest request) {
        if (accountRepository.existsByUsername(request.getAccount().getUsername()))
            throw new AppException(ErrorCode.ACCOUNT_EXISTED);

        Account account = accountMapper.toAccount(request.getAccount());
        account.setPassword(passwordEncoder.encode(request.getAccount().getPassword()));

        HashSet<UserRole> roles = new HashSet<>();
        roles.add(request.getAccount().getRole());
        account.setRoles(roles);

        account = accountRepository.save(account);

        var profile = profileMapper.toProfileCreationRequest(request);
        profile.setAccountId(account.getId());
        profileClient.createProfile(profile);

        return accountMapper.toAccountResponse(account);
    }

    @Transactional
    public AccountResponse createAccount(AccountCreationRequest request) {
        if (accountRepository.existsByUsername(request.getUsername()))
            throw new AppException(ErrorCode.ACCOUNT_EXISTED);

        Account account = accountMapper.toAccount(request);
        account.getRoles().add(UserRole.USER);
        account.setPassword(passwordEncoder.encode(request.getPassword()));
        account = accountRepository.save(account);

        return accountMapper.toAccountResponse(account);
    }

    @Transactional
    @PostAuthorize("returnObject.username == authentication.name")
    public AccountResponse updateAccount(String accountId, AccountUpdateRequest request) {
        Account account = accountRepository
                .findById(accountId)
                .orElseThrow(() -> new AppException(ErrorCode.ACCOUNT_NOT_EXISTED));

        accountMapper.updateAccount(account, request);

        if (request.getPassword() != null && !request.getPassword().isEmpty()) {
            account.setPassword(passwordEncoder.encode(request.getPassword()));
        }

        return accountMapper.toAccountResponse(accountRepository.save(account));
    }

    @PreAuthorize("hasRole('ADMIN')")
    public List<AccountResponse> getAccounts() {
        return accountRepository.findAll().stream()
                .map(accountMapper::toAccountResponse)
                .toList();
    }

    @PostAuthorize("returnObject.id == authentication.name or hasRole('ADMIN')")
    public AccountResponse getAccount(String accountId) {
        Account account = accountRepository
                .findById(accountId)
                .orElseThrow(() -> new AppException(ErrorCode.ACCOUNT_NOT_EXISTED));
        return accountMapper.toAccountResponse(account);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public void deleteAccount(String accountId) {
        if (!accountRepository.existsById(accountId)) throw new AppException(ErrorCode.ACCOUNT_NOT_EXISTED);

        String profileId = null;
        try {
            var profile = profileClient.getProfileByAccountId(accountId);
            if (profile != null) profileId = profile.getId();
        } catch (Exception e) {
            log.error("Cannot fetch profile for account: {}", accountId, e);
        }

        if (profileId != null) {
            try {
                lmsClient.deleteTutor(profileId);
            } catch (Exception e) {
                log.error("Error deleting Tutor data for profile: {}", profileId, e);
                throw new AppException(ErrorCode.DELETE_LMS_FAILED);
            }

            try {
                profileClient.deleteProfile(profileId);
            } catch (Exception e) {
                log.error("Error deleting Neo4j Profile data for profile: {}", profileId, e);
                throw new AppException(ErrorCode.DELETE_PROFILE_FAILED);
            }
        }

        accountRepository.deleteById(accountId);

        log.info("Account {} and related profiles have been deleted", accountId);
    }

    public void addRoleToUser(String accountId, String roleName) {
        Account account = accountRepository
                .findById(accountId)
                .orElseThrow(() -> new AppException(ErrorCode.ACCOUNT_NOT_EXISTED));
        try {
            UserRole role = UserRole.valueOf(roleName.toUpperCase());
            account.getRoles().add(role);
            accountRepository.save(account);
        } catch (IllegalArgumentException e) {
            throw new AppException(ErrorCode.INVALID_ROLE);
        }
    }
}
