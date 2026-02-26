package vn.edu.hcmut.communication.repository.httpclient;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import vn.edu.hcmut.communication.dto.request.IntrospectRequest;
import vn.edu.hcmut.communication.dto.response.APIResponse;
import vn.edu.hcmut.communication.dto.response.IntrospectResponse;

@FeignClient(name = "identity-service", url = "${app.services.identity.url}")
public interface IdentityClient {
    @PostMapping("/auth/introspect")
    APIResponse<IntrospectResponse> introspect(@RequestBody IntrospectRequest request);

}
