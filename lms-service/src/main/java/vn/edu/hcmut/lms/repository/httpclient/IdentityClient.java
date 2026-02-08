package vn.edu.hcmut.lms.repository.httpclient;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "identity-service", url = "${app.services.identity}")
public interface IdentityClient {
    @PostMapping("/internal/accounts/{accountId}/roles")
    void addRole(@PathVariable("accountId") String accountId, @RequestBody String role);
}
