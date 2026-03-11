package vn.edu.hcmut.identity.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import vn.edu.hcmut.identity.dto.response.PageResponse;
import vn.edu.hcmut.identity.service.AccountService;

@Slf4j
@RestController
@RequestMapping("/accounts")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "account management", description = "APIs for managing user accounts, registration, and role assignments")
public class AccountController {

    AccountService accountService;

    @Operation(
            summary = "Register a new user",
            description = "Creates a new identity account and triggers the creation of the user's external profile."
    )
    @PostMapping("/registration")
    APIResponse<AccountResponse> createUser(@RequestBody @Valid UserCreationRequest request) {
        return APIResponse.<AccountResponse>builder()
                .result(accountService.createUser(request))
                .build();
    }

    @Operation(
            summary = "Get all accounts (paginated)",
            description = "Retrieves a paginated list of all accounts in the system. Requires ADMIN role."
    )
    @GetMapping
    public APIResponse<PageResponse<AccountResponse>> getAccounts(
            @Parameter(description = "Page number (1-based index)", example = "1")
            @RequestParam(defaultValue = "1") int page,

            @Parameter(description = "Number of records per page", example = "10")
            @RequestParam(defaultValue = "10") int size) {

        return APIResponse.<PageResponse<AccountResponse>>builder()
                .result(accountService.getAccounts(page, size))
                .build();
    }

    @Operation(
            summary = "Get account details",
            description = "Retrieves specific information about an account. " +
                    "Users can only fetch their own account unless they are an ADMIN."
    )
    @GetMapping("/{accountId}")
    APIResponse<AccountResponse> getAccount(@PathVariable("accountId") String accountId) {
        return APIResponse.<AccountResponse>builder()
                .result(accountService.getAccount(accountId))
                .build();
    }

    @Operation(
            summary = "Update account information",
            description = "Updates an account's details, such as password. " +
                    "Users can only update their own account unless they are an ADMIN."
    )
    @PatchMapping("/{accountId}")
    APIResponse<AccountResponse> updateAccount(
            @PathVariable String accountId,
            @RequestBody @Valid AccountUpdateRequest request) {
        return APIResponse.<AccountResponse>builder()
                .result(accountService.updateAccount(accountId, request))
                .build();
    }

    @Operation(
            summary = "Delete an account",
            description = "Permanently deletes an account and cascades the deletion to LMS and Profile services. " +
                    "Requires ADMIN role."
    )
    @DeleteMapping("/{accountId}")
    APIResponse<String> deleteAccount(@PathVariable String accountId) {
        accountService.deleteAccount(accountId);
        return APIResponse.<String>builder().result("Account has been deleted").build();
    }
}
