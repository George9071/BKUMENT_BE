package vn.edu.hcmut.lms.repository.httpclient;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

@FeignClient(name = "identity-service", url = "${app.services.identity}")
public interface IdentityClient {
    @PostMapping("/internal/accounts/{accountId}/roles/{roleName}")
    void addRole(
            @PathVariable("accountId") String accountId,
            @PathVariable("roleName") String roleName);

    @DeleteMapping("/internal/accounts/{accountId}/roles/{roleName}")
    void removeRole(
            @PathVariable("accountId") String accountId,
            @PathVariable("roleName") String roleName);
}
