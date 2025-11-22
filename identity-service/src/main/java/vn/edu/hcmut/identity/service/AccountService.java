package vn.edu.hcmut.identity.service;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmut.identity.dto.request.AccountCreationRequest;
import vn.edu.hcmut.identity.dto.request.AccountUpdateRequest;
import vn.edu.hcmut.identity.dto.response.AccountResponse;
import vn.edu.hcmut.identity.entity.Account;
import vn.edu.hcmut.identity.exception.AppException;
import vn.edu.hcmut.identity.exception.ErrorCode;
import vn.edu.hcmut.identity.mapper.AccountMapper;
import vn.edu.hcmut.identity.repository.AccountRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AccountService {

    AccountRepository accountRepository;
    AccountMapper accountMapper;
    PasswordEncoder passwordEncoder;

    @Transactional
    public Account createAccount(AccountCreationRequest request) {
        if (accountRepository.existsByUsername(request.getUsername()))
            throw new AppException(ErrorCode.ACCOUNT_EXISTED);

        Account account = accountMapper.toAccount(request);
        account.setRole(request.getRole());
        account.setPassword(passwordEncoder.encode(request.getPassword()));

        return accountRepository.save(account);
    }

    @Transactional
    public Account updateAccount(String accountId, AccountUpdateRequest request) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AppException(ErrorCode.ACCOUNT_NOT_EXISTED));

        accountMapper.updateAccount(account, request);
        account.setPassword(passwordEncoder.encode(request.getPassword()));

        return accountRepository.save(account);
    }

//    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AUTHORIZE_ADMIN')")
    public List<Account> getAccounts() {
        return accountRepository.findAll();
    }

//    @PostAuthorize("returnObject.username == authentication.name or hasRole('ADMIN')")
    public AccountResponse getAccount(String accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AppException(ErrorCode.ACCOUNT_NOT_EXISTED));
        return accountMapper.toAccountResponse(account);
    }

//    @PreAuthorize("hasRole('ADMIN') or hasAuthority('AUTHORIZE_ADMIN')")
    public void deleteAccount(String accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AppException(ErrorCode.ACCOUNT_NOT_EXISTED));

        accountRepository.delete(account);
    }
}
