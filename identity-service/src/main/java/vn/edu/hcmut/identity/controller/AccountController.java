package vn.edu.hcmut.identity.controller;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.*;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import vn.edu.hcmut.identity.dto.request.AccountUpdateRequest;
import vn.edu.hcmut.identity.dto.request.UserCreationRequest;
import vn.edu.hcmut.identity.dto.response.APIResponse;
import vn.edu.hcmut.identity.dto.response.AccountResponse;
import vn.edu.hcmut.identity.service.AccountService;

@Slf4j
@RestController
@RequestMapping("/accounts")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AccountController {

    AccountService accountService;

    @PostMapping("/registration")
    APIResponse<AccountResponse> createUser(@RequestBody @Valid UserCreationRequest request) {
        return APIResponse.<AccountResponse>builder()
                .result(accountService.createUser(request))
                .build();
    }

    @GetMapping
    APIResponse<List<AccountResponse>> getAccounts() {
        return APIResponse.<List<AccountResponse>>builder()
                .result(accountService.getAccounts())
                .build();
    }

    @GetMapping("/{accountId}")
    APIResponse<AccountResponse> getAccount(@PathVariable("accountId") String accountId) {
        return APIResponse.<AccountResponse>builder()
                .result(accountService.getAccount(accountId))
                .build();
    }

    @PatchMapping("/{accountId}")
    APIResponse<AccountResponse> updateAccount(
            @PathVariable String accountId, @RequestBody AccountUpdateRequest request) {
        return APIResponse.<AccountResponse>builder()
                .result(accountService.updateAccount(accountId, request))
                .build();
    }

        @DeleteMapping("/{accountId}")
        APIResponse<String> deleteAccount(@PathVariable String accountId) {
            accountService.deleteAccount(accountId);
            return APIResponse.<String>builder().result("Account has been deleted").build();
        }
}
