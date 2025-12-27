package vn.edu.hcmut.identity.service;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
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
import vn.edu.hcmut.identity.repository.httpclient.ProfileClient;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AccountService {

    AccountRepository accountRepository;
    AccountMapper accountMapper;
    PasswordEncoder passwordEncoder;
    ProfileClient profileClient;
    ProfileMapper profileMapper;

    @Transactional
    public AccountResponse createUser(UserCreationRequest request) {
        if (accountRepository.existsByUsername(request.getAccount().getUsername()))
            throw new AppException(ErrorCode.ACCOUNT_EXISTED);

        Account account = accountMapper.toAccount(request.getAccount());
        account.setRole(request.getAccount().getRole());
        account.setPassword(passwordEncoder.encode(request.getAccount().getPassword()));
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
        account.setRole(request.getRole());
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
        return accountRepository.findAll().stream().map(accountMapper::toAccountResponse).toList();
    }

    @PostAuthorize("returnObject.id == authentication.name or hasRole('ADMIN')")
    public AccountResponse getAccount(String accountId) {
        Account account = accountRepository
                .findById(accountId)
                .orElseThrow(() -> new AppException(ErrorCode.ACCOUNT_NOT_EXISTED));
        return accountMapper.toAccountResponse(account);
    }

    //    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AUTHORIZE_ADMIN')")
    public void deleteAccount(String accountId) {
        Account account = accountRepository
                .findById(accountId)
                .orElseThrow(() -> new AppException(ErrorCode.ACCOUNT_NOT_EXISTED));

        accountRepository.delete(account);
    }
}
