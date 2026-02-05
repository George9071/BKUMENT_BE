package vn.edu.hcmut.identity.controller;

import org.springframework.web.bind.annotation.*;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import vn.edu.hcmut.identity.service.AccountService;

@RestController
@RequestMapping("/internal/accounts")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class InternalAccountController {
    AccountService accountService;

    @PostMapping("/{accountId}/roles")
    public void addRole(@PathVariable String accountId, @RequestBody String role) {
        accountService.addRoleToUser(accountId, role);
    }
}
