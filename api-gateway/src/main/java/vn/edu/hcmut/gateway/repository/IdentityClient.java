package vn.edu.hcmut.gateway.repository;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.PostExchange;
import reactor.core.publisher.Mono;
import vn.edu.hcmut.gateway.dto.request.IntrospectRequest;
import vn.edu.hcmut.gateway.dto.response.APIResponse;
import vn.edu.hcmut.gateway.dto.response.IntrospectResponse;

public interface IdentityClient {
    @PostExchange(url = "/auth/introspect", contentType = MediaType.APPLICATION_JSON_VALUE)
    Mono<APIResponse<IntrospectResponse>> introspect(@RequestBody IntrospectRequest request);
}
