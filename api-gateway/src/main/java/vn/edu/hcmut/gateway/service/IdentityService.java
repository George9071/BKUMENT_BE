package vn.edu.hcmut.gateway.service;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import vn.edu.hcmut.gateway.dto.request.IntrospectRequest;
import vn.edu.hcmut.gateway.dto.response.APIResponse;
import vn.edu.hcmut.gateway.dto.response.IntrospectResponse;
import vn.edu.hcmut.gateway.repository.IdentityClient;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class IdentityService {

    IdentityClient identityClient;

    public Mono<APIResponse<IntrospectResponse>> introspect(String token){
        return identityClient.introspect(IntrospectRequest.builder()
                .token(token)
                .build());
    }
}
