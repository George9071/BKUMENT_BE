package vn.edu.hcmut.identity.controller;

import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import vn.edu.hcmut.identity.dto.request.AccountCreationRequest;
import vn.edu.hcmut.identity.dto.request.AccountUpdateRequest;
import vn.edu.hcmut.identity.dto.request.UserCreationRequest;
import vn.edu.hcmut.identity.dto.response.APIResponse;
import vn.edu.hcmut.identity.dto.response.AccountResponse;
import vn.edu.hcmut.identity.mapper.AccountMapper;
import vn.edu.hcmut.identity.service.AccountService;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/accounts")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AccountController {

    AccountService accountService;
    AccountMapper accountMapper;

    @PostMapping("/users")
    APIResponse<AccountResponse> createUser(@RequestBody @Valid UserCreationRequest request){
        APIResponse<AccountResponse> response = new APIResponse<>();
        response.setResult(accountService.createUser(request));
        return response;
    }

    @PostMapping
    APIResponse<AccountResponse> createAccount(@RequestBody @Valid AccountCreationRequest request){
        APIResponse<AccountResponse> apiResponse = new APIResponse<>();
        apiResponse.setResult(accountService.createAccount(request));
        return apiResponse;
    }

    @GetMapping
    ResponseEntity<APIResponse<List<AccountResponse>>> getAccounts(){
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        log.info("auth: {}", authentication);
        log.info("Username: {}", authentication.getName());
        authentication.getAuthorities().forEach(grantedAuthority -> log.info(grantedAuthority.getAuthority()));

        List<AccountResponse> result = accountService.getAccounts().stream()
                .map(accountMapper::toAccountResponse)
                .toList();

        return ResponseEntity.ok(APIResponse.<List<AccountResponse>>builder()
                .result(result)
                .build());
    }

    @PutMapping("/{accountId}")
    APIResponse<AccountResponse> updateAccount(@PathVariable String accountId, @RequestBody AccountUpdateRequest request){
        return APIResponse.<AccountResponse>builder()
                .result(accountMapper.toAccountResponse(accountService.updateAccount(accountId, request)))
                .build();
    }

    @DeleteMapping("/{accountId}")
    APIResponse<String> deleteAccount(@PathVariable String accountId){
        accountService.deleteAccount(accountId);
        return APIResponse.<String>builder()
                .result("Account has been deleted")
                .build();
    }
}
