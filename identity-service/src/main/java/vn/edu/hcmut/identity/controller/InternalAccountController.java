package vn.edu.hcmut.identity.controller;

import org.springframework.web.bind.annotation.*;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import vn.edu.hcmut.identity.constant.UserRole;
import vn.edu.hcmut.identity.service.AccountService;

@RestController
@RequestMapping("/internal/accounts")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class InternalAccountController {
    AccountService accountService;

    @PostMapping("/{accountId}/roles/{role}")
    public void addRole(@PathVariable String accountId, @PathVariable UserRole role) {
        accountService.addRoleToUser(accountId, role);
    }

    @DeleteMapping("/{accountId}/roles/{role}")
    public void removeRole(@PathVariable("accountId") String accountId, @PathVariable UserRole role) {
        accountService.removeRole(accountId, role);
    }
}
