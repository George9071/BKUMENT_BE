package vn.edu.hcmut.gateway.configuration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import vn.edu.hcmut.gateway.dto.response.APIResponse;
import vn.edu.hcmut.gateway.service.IdentityService;

import java.util.Arrays;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PACKAGE, makeFinal = true)
public class AuthenticationFilter implements GlobalFilter, Ordered {

    IdentityService identityService;
    ObjectMapper objectMapper;

    /**
     * Array of regex patterns for endpoints that bypass authentication (login, registration, etc).
     */
    @NonFinal
    private String[] publicEndpoints = {
            "/identity/auth/.*",
            "/identity/accounts/registration",
    };

    @Value("${app.api-prefix}")
    @NonFinal
    private String apiPrefix;

    /**
     * The core filtering logic applied to all requests passing through the gateway.
     *
     * @param exchange the current server exchange (contains request/response)
     * @param chain    provides a way to delegate to the next filter
     * @return a Mono that indicates when request processing is complete
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
//        log.info("Enter authentication filter....");

        // CORS preflight requests (OPTIONS) don't carry headers like Authorization,
        // so they must be allowed to pass through to avoid CORS errors on the frontend.d.
        if (exchange.getRequest().getMethod().name().equals("OPTIONS")) {
            return chain.filter(exchange);
        }

        // Bypass authentication for explicitly defined public endpoints
        if (isPublicEndpoint(exchange.getRequest())) {
            return chain.filter(exchange);
        }

        // Extract the Authorization header
        List<String> authHeader = exchange.getRequest().getHeaders().get(HttpHeaders.AUTHORIZATION);
        if (CollectionUtils.isEmpty(authHeader)) {
            return unauthenticated(exchange.getResponse());
        }

        String token = authHeader.get(0).replace("Bearer ", "");
//        log.info("Token: {}", token);

        // Verify the token by calling the Identity Service asynchronously
        return identityService.introspect(token).flatMap(introspectResponse -> {
            // If the token is valid, continue the filter chain to route the request
            if (introspectResponse.getResult().isValid())
                return chain.filter(exchange);
            else
                return unauthenticated(exchange.getResponse());
        }).onErrorResume(throwable -> unauthenticated(exchange.getResponse()));
    }

    // Defines the order of this filter
    @Override
    public int getOrder() {
        return -1;
    }

    private boolean isPublicEndpoint(ServerHttpRequest request){
        String path = request.getURI().getPath();

        if (path.contains("/v3/api-docs") || path.contains("/swagger-ui")) return true;

        return Arrays.stream(publicEndpoints)
                .anyMatch(s -> path.matches(apiPrefix + s));
    }

    // Helper method to write a 401 Unauthorized response to the client
    Mono<Void> unauthenticated(ServerHttpResponse response){
        APIResponse<?> apiResponse = APIResponse.builder()
                .code(1401)
                .message("Unauthenticated")
                .build();

        String body = null;
        try {
            body = objectMapper.writeValueAsString(apiResponse);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);

        return response.writeWith(
                Mono.just(response.bufferFactory().wrap(body.getBytes())));
    }
}
