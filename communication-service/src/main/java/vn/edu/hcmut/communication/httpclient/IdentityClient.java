package vn.edu.hcmut.communication.httpclient;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import vn.edu.hcmut.communication.messaging.dto.request.IntrospectRequest;
import vn.edu.hcmut.communication.dto.response.APIResponse;
import vn.edu.hcmut.communication.messaging.dto.response.IntrospectResponse;

@FeignClient(name = "identity-service", url = "${app.services.identity.url}")
public interface IdentityClient {
    @PostMapping("/auth/introspect")
    APIResponse<IntrospectResponse> introspect(@RequestBody IntrospectRequest request);

}
